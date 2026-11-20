package com.fastx.runner

import android.opengl.GLES30

class Prog(vs: String, fs: String) {
    val id: Int
    private val locs = HashMap<String, Int>()

    init {
        val v = compile(GLES30.GL_VERTEX_SHADER, vs)
        val f = compile(GLES30.GL_FRAGMENT_SHADER, fs)
        id = GLES30.glCreateProgram()
        GLES30.glAttachShader(id, v)
        GLES30.glAttachShader(id, f)
        GLES30.glLinkProgram(id)
        val st = IntArray(1)
        GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, st, 0)
        if (st[0] == 0) throw RuntimeException("Link error: " + GLES30.glGetProgramInfoLog(id))
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src)
        GLES30.glCompileShader(s)
        val st = IntArray(1)
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, st, 0)
        if (st[0] == 0) throw RuntimeException("Shader error: " + GLES30.glGetShaderInfoLog(s) + "\n" + src)
        return s
    }

    fun u(n: String): Int = locs.getOrPut(n) { GLES30.glGetUniformLocation(id, n) }
    fun use() = GLES30.glUseProgram(id)
}

object Shaders {
    const val FULL_VS = """#version 300 es
out vec2 v_uv;
void main(){
  vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
  v_uv = p;
  gl_Position = vec4(p * 2.0 - 1.0, 0.9999, 1.0);
}
"""

    const val COMMON = """
precision highp float;
uniform vec3 u_cam; uniform vec3 u_sun; uniform vec3 u_sunCol; uniform vec3 u_skyTop; uniform vec3 u_skyHor;
uniform vec3 u_gnd; uniform vec3 u_fogCol;
uniform float u_fog; uniform float u_night; uniform float u_flash; uniform float u_time; uniform float u_wet;
uniform vec3 u_hlPos; uniform vec3 u_hlDir; uniform float u_hlOn;
float hash12(vec2 p){ vec3 p3=fract(vec3(p.xyx)*.1031); p3+=dot(p3,p3.yzx+33.33); return fract((p3.x+p3.y)*p3.z); }
float noise(vec2 p){ vec2 i=floor(p); vec2 f=fract(p); f=f*f*(3.-2.*f);
  return mix(mix(hash12(i),hash12(i+vec2(1,0)),f.x),mix(hash12(i+vec2(0,1)),hash12(i+vec2(1,1)),f.x),f.y); }
vec3 sky(vec3 d){
  vec3 c=mix(u_skyHor,u_skyTop,pow(clamp(d.y,0.,1.),0.55));
  if(d.y<0.) c=mix(u_skyHor,u_gnd*0.5,clamp(-d.y*4.,0.,1.));
  float s=max(dot(d,normalize(u_sun)),0.);
  c+=u_sunCol*(pow(s,900.)*8.+pow(s,48.)*0.6+pow(s,6.)*0.12);
  c+=vec3(0.7,0.8,1.)*u_flash*0.6;
  return c;
}
vec3 applyFog(vec3 col, vec3 pos){
  vec3 d=pos-u_cam; float dist=max(length(d),0.001);
  float f=1.-exp(-dist*u_fog);
  vec3 vd=d/dist; float sc=pow(max(dot(vd,normalize(u_sun)),0.),6.);
  vec3 fc=u_fogCol+u_sunCol*sc*0.12+vec3(0.7,0.8,1.)*u_flash*0.5;
  return mix(col,fc,clamp(f,0.,1.));
}
vec3 fixc(vec3 c){ if(!(c.r>=0.&&c.g>=0.&&c.b>=0.)) return u_fogCol; return min(c,vec3(200.)); }
vec3 headlight(vec3 p, vec3 N){
  vec3 d=p-u_hlPos; float dist=max(length(d),0.001); vec3 ld=d/dist;
  float cone=smoothstep(0.86,0.97,dot(ld,normalize(u_hlDir)));
  float att=1./(1.+dist*dist*0.004);
  float lam=max(dot(N,-ld),0.);
  return vec3(1.,.95,.85)*cone*att*lam*14.*u_hlOn;
}
"""

    const val OBJ_VS = """#version 300 es
layout(location=0) in vec3 a_pos;
layout(location=1) in vec3 a_nrm;
layout(location=2) in float a_part;
uniform mat4 u_vp; uniform mat4 u_model;
out vec3 v_pos; out vec3 v_nrm; out vec3 v_lpos; flat out float v_part;
void main(){
  vec4 w=u_model*vec4(a_pos,1.);
  v_pos=w.xyz; v_nrm=mat3(u_model)*a_nrm; v_lpos=a_pos; v_part=a_part;
  gl_Position=u_vp*w;
}
"""

    const val OBJ_FS = """#version 300 es
""" + COMMON + """
in vec3 v_pos; in vec3 v_nrm; in vec3 v_lpos; flat in float v_part;
uniform vec3 u_color; uniform vec3 u_emis; uniform float u_mode; uniform float u_win; uniform float u_brake;
out vec4 o;
void main(){
  if(u_mode>1.5){ float d=length(v_lpos.xz*2.); float a=clamp((1.-d)/0.65,0.,1.); a=a*a*(3.-2.*a)*0.55; o=vec4(0.,0.,0.,a); return; }
  float nl0=length(v_nrm); vec3 N=nl0>1e-5?v_nrm/nl0:vec3(0.,1.,0.); vec3 V=normalize(u_cam-v_pos);
  if(dot(N,V)<0.) N=-N;
  vec3 L=normalize(u_sun);
  vec3 base=u_color; vec3 emis=u_emis; float F0=0.04; float gloss=40.; float envK=0.;
  int part=int(v_part+0.5);
  bool car=u_mode>0.5;
  if(car){
    if(part==0){ gloss=220.; envK=1.0; }
    else if(part==1){ base=vec3(0.01,0.015,0.02); gloss=400.; envK=1.6; F0=0.08; }
    else if(part==2){ base=vec3(0.012); gloss=20.; envK=0.15; }
    else if(part==3){ base=vec3(0.9); emis=vec3(9.,8.6,7.6); }
    else if(part==4){ base=vec3(0.2,0.,0.); emis=vec3(3.5,0.15,0.1)*(1.+u_brake*3.5); }
    else if(part==5){ base=vec3(0.75,0.77,0.8); gloss=300.; envK=1.2; F0=0.5; }
  } else if(u_win>0.001 && abs(N.y)<0.5){
    float hc = abs(N.x)>abs(N.z) ? v_pos.z : v_pos.x;
    vec2 cell=vec2(hc*0.28, v_pos.y*0.22);
    vec2 fc=fract(cell); vec2 ic=floor(cell);
    float lit=step(0.45,hash12(ic));
    float inside=step(0.2,fc.x)*step(fc.x,0.8)*step(0.25,fc.y)*step(fc.y,0.75);
    vec3 wc=mix(vec3(1.,.75,.4),vec3(.6,.8,1.),hash12(ic+7.));
    emis+=wc*lit*inside*u_win*3.;
    base=mix(base,vec3(0.02,0.03,0.04),inside*0.6);
  }
  vec3 amb=mix(u_gnd,u_skyTop*0.8+u_skyHor*0.4,N.y*0.5+0.5)*0.9;
  float ndl=max(dot(N,L),0.);
  vec3 col=base*(u_sunCol*ndl+amb+headlight(v_pos,N));
  vec3 R=reflect(-V,N);
  float fres=F0+(1.-F0)*pow(1.-max(dot(N,V),0.),5.);
  vec3 env=sky(R);
  if(R.y<0.) env=mix(env,u_gnd*(u_sunCol*0.15+0.2),0.6);
  col+=env*fres*envK;
  if(car){
    vec3 H=normalize(L+V);
    col+=u_sunCol*pow(max(dot(N,H),0.),gloss)*(gloss/60.)*ndl*(part==2?0.2:1.0);
    if(part==0) col+=env*base*0.35;
  }
  col+=emis;
  col=applyFog(col,v_pos);
  o=vec4(fixc(col),0.);
}
"""

    const val GROUND_VS = """#version 300 es
layout(location=0) in vec3 a_pos;
layout(location=1) in float a_lat;
uniform mat4 u_vp;
out vec3 v_pos; out float v_lat;
void main(){ v_pos=a_pos; v_lat=a_lat; gl_Position=u_vp*vec4(a_pos,1.); }
"""

    const val GROUND_FS = """#version 300 es
""" + COMMON + """
in vec3 v_pos; in float v_lat;
out vec4 o;
void main(){
  float lat=v_lat; float al=abs(lat);
  vec3 N=cross(dFdx(v_pos),dFdy(v_pos)); float nl1=length(N); N=nl1>1e-12?N/nl1:vec3(0.,1.,0.); if(N.y<0.) N=-N;
  vec3 V=normalize(u_cam-v_pos); vec3 L=normalize(u_sun);
  float n1=noise(v_pos.xz*0.9); float n2=noise(v_pos.xz*0.07); float n3=noise(v_pos.xz*0.012+5.);
  vec3 albedo; float refl=0.; float gloss=30.; float isRoad=0.;
  float wetM=u_wet*(0.35+0.65*smoothstep(0.3,0.65,noise(v_pos.xz*vec2(0.35,0.08)+2.)));
  if(al<8.0){
    isRoad=1.;
    albedo=vec3(0.05)*(0.75+0.5*n1)+0.012*n2;
    float w=fwidth(lat)+0.03;
    float ld=min(min(abs(lat+4.),abs(lat)),abs(lat-4.));
    float dash=step(mod(v_pos.z,12.),6.);
    float mark=(1.-smoothstep(0.09,0.09+w,ld))*dash;
    float edge=1.-smoothstep(0.11,0.11+w,abs(al-7.55));
    albedo=mix(albedo,vec3(0.75),max(mark,edge));
    refl=0.22+0.7*wetM; gloss=mix(60.,700.,wetM);
    albedo*=1.-0.35*wetM;
  } else if(al<13.0){
    albedo=u_gnd*1.3*(0.7+0.5*n1)+vec3(0.05);
  } else {
    albedo=u_gnd*(0.55+0.9*n3)*(0.75+0.5*n2);
    float slope=1.-N.y;
    albedo=mix(albedo,vec3(0.25,0.24,0.23)*(0.6+0.8*n1),smoothstep(0.15,0.5,slope));
  }
  float lz=v_pos.z-floor(v_pos.z/40.+0.5)*40.;
  float lx=min(abs(lat-10.),abs(lat+10.));
  float pool=exp(-(lz*lz+lx*lx)/128.);
  vec3 lampL=vec3(1.,.72,.4)*pool*u_night*3.;
  vec3 hl=headlight(v_pos,N);
  vec3 amb=(u_skyTop*0.5+u_skyHor*0.5)*0.9*(0.5+0.5*N.y);
  float ndl=max(dot(N,L),0.);
  vec3 col=albedo*(u_sunCol*ndl+amb+lampL+hl);
  vec3 R=reflect(-V,N);
  float fres=refl*(0.05+0.95*pow(1.-max(dot(N,V),0.),4.));
  vec3 env=sky(R);
  col=mix(col,env,clamp(fres,0.,1.));
  col+=u_sunCol*pow(max(dot(R,L),0.),gloss)*refl*(gloss/100.);
  col+=(lampL*1.5+hl*0.3)*refl*wetM*0.5;
  col=applyFog(col,v_pos);
  o=vec4(fixc(col),isRoad*clamp(refl,0.,1.));
}
"""

    const val SKY_FS = """#version 300 es
""" + COMMON + """
in vec2 v_uv; out vec4 o;
uniform vec3 u_right; uniform vec3 u_up; uniform vec3 u_fwd; uniform float u_tanY; uniform float u_asp; uniform float u_cloud;
float fbm(vec2 p){ float a=.5,s=0.; for(int i=0;i<4;i++){ s+=a*noise(p); p*=2.03; a*=.5; } return s; }
void main(){
  vec2 nd=v_uv*2.-1.;
  vec3 d=normalize(u_fwd+u_right*nd.x*u_tanY*u_asp+u_up*nd.y*u_tanY);
  vec3 c=sky(d);
  if(d.y>0.01){
    vec2 p=d.xz/(d.y+0.3)*1.3+vec2(u_time*0.012,0.);
    float cl=smoothstep(0.5-0.25*u_cloud,0.95,fbm(p))*u_cloud;
    vec3 cc=mix(u_skyHor,u_skyTop,0.35)*0.85+u_sunCol*0.05+vec3(0.7,0.8,1.)*u_flash*1.2;
    c=mix(c,cc,clamp(cl,0.,1.)*smoothstep(0.0,0.2,d.y));
  }
  float st=step(0.9975,hash12(floor(d.xz/(d.y+0.2)*220.)))*u_night*smoothstep(0.05,0.4,d.y);
  c+=vec3(st)*1.5;
  o=vec4(fixc(c),0.);
}
"""

    const val PART_VS = """#version 300 es
layout(location=0) in vec3 a_pos;
layout(location=1) in vec2 a_uv;
layout(location=2) in vec4 a_col;
uniform mat4 u_vp;
out vec2 v_uv; out vec4 v_col;
void main(){ v_uv=a_uv; v_col=a_col; gl_Position=u_vp*vec4(a_pos,1.); }
"""

    const val PART_FS = """#version 300 es
precision highp float;
in vec2 v_uv; in vec4 v_col; out vec4 o;
uniform float u_add;
void main(){
  float d=length(v_uv*2.-1.);
  float m=smoothstep(1.,0.,d); m*=m;
  if(u_add>0.5) o=vec4(v_col.rgb*m*v_col.a,1.);
  else o=vec4(v_col.rgb,m*v_col.a);
}
"""

    const val POST_FS = """#version 300 es
precision highp float;
in vec2 v_uv; out vec4 o;
uniform sampler2D u_tex;
uniform vec2 u_texel; uniform vec2 u_sunUV;
uniform float u_rays; uniform float u_blur; uniform float u_aberr; uniform float u_vig; uniform float u_exp;
uniform float u_bloom; uniform float u_flash; uniform float u_damage; uniform float u_time; uniform float u_ssr;
uniform vec3 u_tint;
float hash(vec2 p){ return fract(sin(dot(p,vec2(12.9898,78.233)))*43758.5453); }
vec3 aces(vec3 x){ return clamp((x*(2.51*x+0.03))/(x*(2.43*x+0.59)+0.14),0.,1.); }
vec4 T(vec2 p, float l){
  vec4 c=textureLod(u_tex,p,l);
  if(!(c.r>=0.&&c.g>=0.&&c.b>=0.)) return vec4(0.);
  return vec4(min(c.rgb,vec3(64.)),clamp(c.a,0.,1.));
}
void main(){
  vec2 uv=v_uv;
  vec2 dir=uv-vec2(0.5,0.45);
  vec3 refl=vec3(0.);
  float a0=T(uv,0.).a;
  if(u_ssr>0.5 && a0>0.02){
    float stp=0.014; float yb=-1.;
    for(int i=1;i<=20;i++){
      float yy=uv.y+float(i)*stp;
      if(yy>=1.) break;
      if(T(vec2(uv.x,yy),0.).a<0.01){ yb=float(i)*stp; break; }
    }
    if(yb>0.){
      float ry=uv.y+2.*yb;
      if(ry<1.){
        float jit=(hash(uv*512.+u_time)-0.5)*0.004;
        vec3 acc=T(vec2(uv.x+jit,ry),0.).rgb;
        acc+=T(vec2(uv.x+jit+0.004,ry),1.).rgb;
        acc+=T(vec2(uv.x+jit-0.004,ry),1.).rgb;
        float ok=step(T(vec2(uv.x,ry),0.).a,0.01);
        refl=acc/3.*ok*(1.-yb/(20.*stp))*a0*0.85;
      }
    }
  }
  vec3 col=vec3(0.);
  for(int i=0;i<8;i++){
    float t=float(i)/7.;
    vec2 p=uv-dir*u_blur*t;
    if(u_aberr>0.0001){
      col.r+=T(p+dir*u_aberr,0.).r;
      col.g+=T(p,0.).g;
      col.b+=T(p-dir*u_aberr,0.).b;
    } else col+=T(p,0.).rgb;
  }
  col/=8.;
  vec3 bl=vec3(0.);
  for(int l=3;l<=5;l++){
    float lod=float(l);
    vec2 off=u_texel*exp2(lod)*0.7;
    vec3 s=T(uv+vec2(off.x,0.),lod).rgb+T(uv-vec2(off.x,0.),lod).rgb
          +T(uv+vec2(0.,off.y),lod).rgb+T(uv-vec2(0.,off.y),lod).rgb;
    bl+=max(s*0.25-0.5,0.)*(1.2-0.15*lod);
  }
  vec3 rays=vec3(0.);
  if(u_rays>0.001){
    vec2 d=(u_sunUV-uv)*(0.95/20.); vec2 p=uv; float w=1.;
    for(int i=0;i<20;i++){
      p+=d;
      vec3 s=T(p,2.).rgb;
      float lum=dot(s,vec3(.3,.59,.11));
      rays+=s*smoothstep(1.5,6.,lum)*w;
      w*=0.95;
    }
    rays*=u_rays/20.;
  }
  col+=refl+bl*u_bloom+rays;
  col*=u_exp;
  col+=vec3(0.6,0.7,1.)*u_flash*0.25;
  col=aces(col)*u_tint;
  float lum=dot(col,vec3(.299,.587,.114));
  col=mix(vec3(lum),col,1.12);
  col*=1.-u_vig*dot(dir,dir)*1.8;
  col=mix(col,vec3(0.8,0.05,0.03),u_damage*smoothstep(0.2,0.7,length(dir)));
  col=pow(clamp(col,0.,1.),vec3(1./2.2));
  col+=(hash(uv*vec2(1920.,1080.)+u_time)-0.5)*0.015;
  o=vec4(col,1.);
}
"""
}
