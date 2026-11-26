// Ejercita kvs(): decodificación de las URL function/0/ y su verificación con la URL real que ya pidió el reproductor.
const {makeEnv,vm,fs}=require('./harness.js');
const js=fs.readFileSync('SNIFF_JS.js','utf8');
// Copia del algoritmo de referencia (yt-dlp) para fabricar un par (cifrado, real) coherente
function licTok(code){code=code.replace(/\$/g,'');const lv=[...code].map(Number);const mod=code.replace(/0/g,'1');const c=Math.floor(mod.length/2);
  const fh=parseInt(mod.slice(0,c+1)),bh=parseInt(mod.slice(c));const q=String(4*Math.abs(fh-bh)).slice(0,c+1);const out=[];
  for(let i=0;i<q.length;i++)for(let o=0;o<4;o++)out.push(((lv[i+o]||0)+(+q[i]))%10);return out;}
function dec(H,tok){const idx=[...Array(32).keys()];let acc=0;for(let s=31;s>=0;s--){acc+=tok[s];const d=(s+acc)%32;[idx[s],idx[d]]=[idx[d],idx[s]];}return idx.map(i=>H[i]).join('');}
(async()=>{
  const code='$285739281649362',tok=licTok(code);
  const enc720='0123456789abcdef0123456789abcdef',enc1080='fedcba9876543210fedcba9876543210';
  const mk=(h,q)=>`https://pimpbunny.com/get_file/9/${h}/1000/2000/2000_${q}p.mp4/`;
  const real720=mk(dec(enc720,tok),720);
  const rect={left:0,top:0,right:360,bottom:200,width:360,height:200};
  const video={tagName:'VIDEO',currentSrc:real720,src:real720,_r:rect,getBoundingClientRect(){return rect},paused:true,ended:false,muted:false,duration:600,videoHeight:720,play(){return Promise.resolve()},pause(){},load(){},parentElement:null,className:'',id:'',getAttribute(){return null}};
  const {ctx,calls}=makeEnv({url:'https://pimpbunny.com/videos/x/',video});
  ctx.flashvars={license_code:code,video_url:'function/0/'+mk(enc720,720),video_url_text:'720p',video_alt_url:'function/0/'+mk(enc1080,1080),video_alt_url_text:'1080p'};
  vm.createContext(ctx); vm.runInContext(js,ctx);
  await new Promise(r=>setTimeout(r,1800));
  const notes=calls.filter(c=>c[0]==='note').map(c=>c[1]);
  const sent=calls.filter(c=>c[0]==='media2').map(c=>c[1]);
  console.log('notas:',notes.join(' | '));
  console.log('URL enviadas:',sent.map(u=>u.slice(-60)));
  const want1080=mk(dec(enc1080,tok),1080);
  console.log(notes.some(n=>/algoritmo 0 verificado/.test(n))?'OK calibración':'FALLO calibración',
              sent.includes(want1080)?'| OK URL 1080p decodificada correcta':'| FALLO URL 1080p');
  process.exit(notes.some(n=>/algoritmo 0 verificado/.test(n))&&sent.includes(want1080)?0:1);
})();
