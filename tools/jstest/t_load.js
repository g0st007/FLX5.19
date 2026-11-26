// El script de detección debe cargar sin errores y NO tocar el reproductor de la página (no hay recorrido de calidades).
const {makeEnv,vm,fs}=require('./harness.js');
const js=fs.readFileSync('SNIFF_JS.js','utf8');
const rect={left:0,top:0,right:360,bottom:200,width:360,height:200};
function vid(src){return {tagName:'VIDEO',currentSrc:src,src,_r:rect,getBoundingClientRect(){return rect},paused:true,ended:false,muted:false,duration:600,videoHeight:720,play(){this.played=true;return Promise.resolve()},pause(){},load(){},parentElement:null,className:'',id:'',getAttribute(){return null}};}
(async()=>{
  let fail=0;
  for(const [name,src] of [['PH blob','blob:https://es.pornhub.com/abc'],['m3u8','https://cdn.x.com/master.m3u8'],['mp4 directo (Bunny)','https://pimpbunny.com/get_file/9/h/1/2/x_720p.mp4/']]){
    const v=vid(src); const {ctx,calls}=makeEnv({url:'https://example.com/v',video:v});
    vm.createContext(ctx);
    try{vm.runInContext(js,ctx);}catch(e){console.log(name,'FALLO al cargar:',e.message);fail++;continue;}
    await new Promise(r=>setTimeout(r,400));
    const bad=[];
    if(typeof ctx.__fluxHarvest==='function')bad.push('existe __fluxHarvest');
    if(v.played)bad.push('llamó play() al video');
    if(calls.some(c=>c[0]==='auto'))bad.push('llamó B.auto');
    console.log(name,bad.length?'FALLO: '+bad.join(', '):'OK (no toca el reproductor)');
    if(bad.length)fail++;
  }
  process.exit(fail?1:0);
})();
