const vm=require('vm'),fs=require('fs');
function makeEnv(scn){
  const calls=[];
  const B=new Proxy({}, {get:(t,name)=>(...a)=>{calls.push([name,...a]);return undefined;}});
  class El{constructor(o={}){Object.assign(this,{tagName:'DIV',className:'',id:'',children:[],parentElement:null,textContent:'',attrs:{},style:{setProperty(){},cssText:''}},o);}
    getBoundingClientRect(){return this._r||{left:0,top:0,right:0,bottom:0,width:0,height:0};}
    getAttribute(k){return this.attrs[k]||null;} setAttribute(k,v){this.attrs[k]=v;}
    querySelectorAll(sel){return (this._q&&this._q(sel))||[];} querySelector(){return null;}
    addEventListener(){} removeEventListener(){} remove(){} closest(){return null;}
    appendChild(){} removeAttribute(){}
    dispatchEvent(ev){ if(this._onEvent)this._onEvent(ev); return true;} }
  const ctx={console,setTimeout,clearTimeout,setInterval,clearInterval,Promise,URL,JSON,Math,Date,Object,Array,String,Number,RegExp,parseInt,parseFloat,isNaN,Error,ReferenceError,Function,Set,Map,encodeURIComponent,decodeURIComponent};
  ctx.window=ctx; ctx.self=ctx; ctx.addEventListener=()=>{}; ctx.removeEventListener=()=>{}; ctx.innerWidth=360; ctx.innerHeight=700; ctx.top=ctx; ctx.parent=ctx;
  ctx.location={href:scn.url,hostname:new URL(scn.url).hostname,origin:new URL(scn.url).origin};
  ctx.performance={getEntriesByType:()=>[]};
  ctx.getComputedStyle=()=>({visibility:'visible',display:'block'});
  ctx.MouseEvent=class{constructor(t,o){this.type=t;Object.assign(this,o||{});}};
  ctx.PointerEvent=ctx.MouseEvent;
  ctx.MutationObserver=class{observe(){}disconnect(){}};
  ctx.fetch=()=>Promise.reject(new Error('no net'));
  ctx.XMLHttpRequest=function(){}; ctx.XMLHttpRequest.prototype={open(){},send(){},addEventListener(){}};
  ctx.HTMLMediaElement=function(){}; ctx.HTMLMediaElement.prototype={play(){},pause(){},load(){}};
  ctx.EventTarget=function(){}; ctx.EventTarget.prototype={addEventListener(){},removeEventListener(){}};
  ctx.Response=function(){};
  ctx.requestAnimationFrame=cb=>setTimeout(cb,0);
  ctx.CSS={escape:s=>s};
  const video=scn.video;
  const body=new El({tagName:'BODY'});
  body._q=scn.bodyQ?scn.bodyQ:()=>[];
  ctx.document={readyState:'complete',scripts:[],body,documentElement:body,currentScript:null,
    querySelectorAll:(sel)=>/video/.test(sel)&&sel.indexOf('li')<0?[video].filter(Boolean):[],
    querySelector:()=>null,
    createElement:(tag)=>{ if(String(tag).toLowerCase()==='a'){ const a=new El({tagName:'A'}); let h='';
        Object.defineProperty(a,'href',{get:()=>h,set:(v)=>{h=v;try{const u=new URL(v,ctx.location.href);a.pathname=u.pathname;a.host=u.host;a.protocol=u.protocol;a.search=u.search;a.hostname=u.hostname;}catch(e){}}}); return a; }
      return new El({tagName:String(tag).toUpperCase()}); },addEventListener(){},removeEventListener(){},
    getElementsByTagName:()=>[]};
  ctx.__fluxBridge=B;
  return {ctx,calls,El};
}
module.exports={makeEnv,vm,fs};
