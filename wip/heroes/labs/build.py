import base64,sys
src,glb,out=sys.argv[1:4]
s=open(src).read()
t=s.replace('__RAYAN__',base64.b64encode(open(glb,'rb').read()).decode()).replace('__SAMI__',base64.b64encode(open(glb,'rb').read()).decode()).replace('__WOLF__',base64.b64encode(open('wolf.glb','rb').read()).decode()).replace('__WING__',base64.b64encode(open('wing.glb','rb').read()).decode()).replace('__HAM__',base64.b64encode(open('hammer_o.glb','rb').read()).decode()).replace('__GOB__',base64.b64encode(open('goblin.glb','rb').read()).decode())
t='<!DOCTYPE html>\n<meta charset="utf-8">\n<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">\n'+t
open(out,'w').write(t)
u=t.replace('https://cdn.jsdelivr.net/npm/three@0.128.0/build/three.min.js','r128/three.min.js').replace('https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/loaders/GLTFLoader.js','r128/GLTFLoader.js').replace('https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/utils/SkeletonUtils.js','r128/SkeletonUtils.js')
open('t_'+out,'w').write(u)
