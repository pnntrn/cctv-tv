#!/usr/bin/env python3
"""Publish a tested signed APK. Requires authenticated gh, apksigner and aapt on PATH."""
import argparse,base64,hashlib,json,pathlib,re,subprocess
REPO='pnntrn/cctv-updates'
CERT='4bb96b0ff9021cc49885f9cdd02d62ec3e58c3704cd65853e8dc2269316c5f9b'
def api(path,method='GET',body=None):
    cmd=['gh','api',path,'--method',method];raw=None
    if body is not None:cmd+=['--input','-'];raw=json.dumps(body)
    return json.loads(subprocess.check_output(cmd,input=raw,text=True))
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk',type=pathlib.Path);parser.add_argument('--notes',required=True)
    args=parser.parse_args();data=args.apk.read_bytes()
    if not 0<len(data)<=64*1024*1024:raise SystemExit('APK outside size limit')
    verified=subprocess.check_output(['apksigner','verify','--print-certs',str(args.apk)],text=True)
    if not re.search(r'Signer #1 certificate SHA-256 digest: '+CERT+r'\b',verified,re.I):raise SystemExit('Unexpected signing certificate')
    badging=subprocess.check_output(['aapt','dump','badging',str(args.apk)],text=True)
    pkg=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",badging)
    sdk=re.search(r"sdkVersion:'(\d+)'",badging)
    if not pkg or pkg[1]!='com.pnntrn.cctv' or not sdk:raise SystemExit('Wrong app or malformed APK')
    code=int(pkg[2]);name=pkg[3];minimum=int(sdk[1])
    if not re.fullmatch(r'[a-zA-Z0-9._-]{1,64}',name):raise SystemExit('Unsafe version name')
    head=api('repos/'+REPO+'/git/ref/heads/main')['object']['sha']
    tree=api('repos/'+REPO+'/git/commits/'+head)['tree']['sha']
    entries=api('repos/'+REPO+'/git/trees/'+tree+'?recursive=1')['tree']
    previous=next((x for x in entries if x['path']=='latest.json'),None)
    if previous:
        old=api('repos/'+REPO+'/git/blobs/'+previous['sha'])
        old=json.loads(base64.b64decode(old['content']))
        if code<=old['versionCode']:raise SystemExit('New version code must exceed published version')
    path=f'apks/cctv-{name}-{code}.apk'
    if any(x['path']==path for x in entries):raise SystemExit('Versioned APK already exists')
    digest=hashlib.sha256(data).hexdigest()
    blob=api('repos/'+REPO+'/git/blobs','POST',{'content':base64.b64encode(data).decode(),'encoding':'base64'})['sha']
    apk_tree=api('repos/'+REPO+'/git/trees','POST',{'base_tree':tree,'tree':[{'path':path,'mode':'100644','type':'blob','sha':blob}]})['sha']
    apk_commit=api('repos/'+REPO+'/git/commits','POST',{'message':f'Add signed CCTV {name} ({code})','tree':apk_tree,'parents':[head]})['sha']
    manifest={'schema':1,'packageName':'com.pnntrn.cctv','versionCode':code,'versionName':name,'minSdk':minimum,'size':len(data),'sha256':digest,'url':f'https://raw.githubusercontent.com/{REPO}/{apk_commit}/{path}','notes':args.notes}
    readme=f'# CCTV Android TV\n\n[Download CCTV {name}]({manifest["url"]}) · {len(data):,} bytes\n\n{args.notes}\n\nUse **Settings → Check for updates** in CCTV 0.3 or later. Android asks you to confirm installation. Keep the same app installed to preserve saved cameras.\n\nThis repository contains public APKs and update information only. Source remains private.\n\nSHA-256: `{digest}`\n\nSigning certificate SHA-256: `{CERT}`\n'
    publish_tree=api('repos/'+REPO+'/git/trees','POST',{'base_tree':apk_tree,'tree':[{'path':'latest.json','mode':'100644','type':'blob','content':json.dumps(manifest,indent=2)+'\n'},{'path':'README.md','mode':'100644','type':'blob','content':readme}]})['sha']
    commit=api('repos/'+REPO+'/git/commits','POST',{'message':f'Publish CCTV {name} ({code}) update feed','tree':publish_tree,'parents':[apk_commit]})['sha']
    api('repos/'+REPO+'/git/refs/heads/main','PATCH',{'sha':commit,'force':False})
    print(f'Published CCTV {name} ({code}) at https://github.com/{REPO}')
if __name__=='__main__':main()
