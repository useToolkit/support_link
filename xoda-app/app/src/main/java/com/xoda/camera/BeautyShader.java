package com.xoda.camera;

/**
 * Entirely new single-sampler GPU color + region retouch shader. Facial
 * geometry deforms real mesh vertices, not fake slider labels.
 */
final class BeautyShader {
    static final String VERTEX = """
            attribute vec2 aPosition;
            attribute vec2 aUv;
            varying vec2 vUv;
            void main() {
                gl_Position=vec4(aPosition,0.0,1.0);
                vUv=aUv;
            }
            """;

    static String fragment(boolean external){
        String first=external?"#extension GL_OES_EGL_image_external : require\n":"";
        return first+FRAGMENT.replace("@SAMPLER@",external?"samplerExternalOES":"sampler2D");
    }
    private static final String FRAGMENT = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            varying vec2 vUv;
            uniform @SAMPLER@ uCamera;
            uniform mat4 uTexTransform;
            uniform vec2 uPixel;
            uniform float uMirror,uFaceValid;
            uniform vec4 uFace,uEyes,uNoseMouth,uCheeks,uBrows;
            uniform vec4 uSkin0,uSkin1,uMakeup0,uMakeup1,uColor,uStyle;

            vec3 source(vec2 uv){
                uv=clamp(uv,vec2(.001),vec2(.999));
                if(uMirror>.5)uv.x=1.0-uv.x;
                vec2 transformed=(uTexTransform*vec4(uv,0.0,1.0)).xy;
                return texture2D(uCamera,transformed).rgb;
            }
            float ellipse(vec2 p,vec2 c,vec2 rad){
                vec2 d=(p-c)/max(rad,vec2(.0001));
                float m=max(0.0,1.0-dot(d,d));
                return m*m;
            }
            void main(){
                vec2 pos=vec2(vUv.x,1.0-vUv.y);
                vec3 original=source(vUv);
                vec3 rgb=original;
                float smoothAmount=uSkin0.x,pores=uSkin0.y;
                float tone=uSkin0.z,darkCircles=uSkin0.w;
                float teeth=uSkin1.x,folds=uSkin1.y,clarity=uSkin1.z,hd=uSkin1.w;

                if(uFaceValid>.5){
                    vec2 faceDim=max(uFace.zw-uFace.xy,vec2(.001));
                    vec2 faceCenter=(uFace.xy+uFace.zw)*.5;
                    float whole=ellipse(pos,faceCenter,faceDim*vec2(.52,.58));
                    float eyeZone=clamp(
                        ellipse(pos,uEyes.xy,faceDim*vec2(.18,.12))+
                        ellipse(pos,uEyes.zw,faceDim*vec2(.18,.12)),0.0,1.0);
                    float mouthZone=ellipse(pos,uNoseMouth.zw,faceDim*vec2(.25,.12));
                    float skin=whole*(1.0-max(eyeZone,mouthZone));

                    float soften=clamp(smoothAmount+.60*pores,0.0,1.0);
                    if(soften>.001&&skin>.001){
                        vec2 d=uPixel*(1.5+3.0*soften);
                        vec3 a=source(vUv+vec2(d.x,0.0));
                        vec3 b=source(vUv-vec2(d.x,0.0));
                        vec3 c=source(vUv+vec2(0.0,d.y));
                        vec3 e=source(vUv-vec2(0.0,d.y));
                        float wa=exp(-dot(a-original,a-original)*44.0);
                        float wb=exp(-dot(b-original,b-original)*44.0);
                        float wc=exp(-dot(c-original,c-original)*44.0);
                        float we=exp(-dot(e-original,e-original)*44.0);
                        vec3 filtered=(original*2.0+a*wa+b*wb+c*wc+e*we)/
                            max(.001,2.0+wa+wb+wc+we);
                        rgb=mix(rgb,filtered,skin*.89*soften);
                    }
                    rgb=mix(rgb,vec3(1.0),.12*tone*skin);

                    float shadowUnder=min(1.0,
                        ellipse(pos,uEyes.xy+vec2(0.0,faceDim.y*.092),
                            faceDim*vec2(.19,.077))+
                        ellipse(pos,uEyes.zw+vec2(0.0,faceDim.y*.092),
                            faceDim*vec2(.19,.077)));
                    float luminance=dot(rgb,vec3(.2126,.7152,.0722));
                    float shade=(1.0-smoothstep(.55,.83,luminance))*
                                    smoothstep(.11,.28,luminance);
                    rgb+=vec3(.12,.118,.113)*darkCircles*shadowUnder*shade;

                    float fold=min(1.0,
                        ellipse(pos,uNoseMouth.xy+vec2(-faceDim.x*.13,faceDim.y*.08),
                            faceDim*vec2(.11,.17))+
                        ellipse(pos,uNoseMouth.xy+vec2(faceDim.x*.13,faceDim.y*.08),
                            faceDim*vec2(.11,.17)));
                    rgb+=vec3(.10,.095,.09)*folds*fold*(1.0-smoothstep(.48,.80,luminance));

                    float whiteMouth=ellipse(pos,uNoseMouth.zw+vec2(0.0,faceDim.y*.015),
                        faceDim*vec2(.17,.043));
                    float chroma=abs(rgb.r-rgb.g)+abs(rgb.g-rgb.b);
                    float enamel=smoothstep(.35,.55,luminance)*
                        (1.0-smoothstep(.12,.27,chroma));
                    rgb=mix(rgb,vec3(.94,.95,1.0),whiteMouth*enamel*.24*teeth);

                    vec2 lipCenter=uNoseMouth.zw+vec2(0.0,faceDim.y*.022);
                    float lipMask=ellipse(pos,lipCenter,faceDim*vec2(.22,.075));
                    rgb=mix(rgb,vec3(.81,.17,.25)*(.66+.35*luminance),
                        lipMask*uMakeup0.x*.48);
                    float blushMask=min(1.0,
                        ellipse(pos,uCheeks.xy,faceDim*vec2(.18,.135))+
                        ellipse(pos,uCheeks.zw,faceDim*vec2(.18,.135)));
                    rgb=mix(rgb,vec3(1.0,.29,.37),blushMask*uMakeup0.y*.27);

                    float shadows=min(1.0,
                        ellipse(pos,uEyes.xy-vec2(0.0,faceDim.y*.06),
                            faceDim*vec2(.18,.075))+
                        ellipse(pos,uEyes.zw-vec2(0.0,faceDim.y*.06),
                            faceDim*vec2(.18,.075)));
                    rgb=mix(rgb,vec3(.39,.24,.31),shadows*uMakeup0.z*.44);

                    float eyelinerMask=min(1.0,
                        ellipse(pos,uEyes.xy,faceDim*vec2(.20,.046))+
                        ellipse(pos,uEyes.zw,faceDim*vec2(.20,.046)));
                    rgb=mix(rgb,vec3(.14,.095,.09),eyelinerMask*uMakeup0.w*.29);
                    float lashes=min(1.0,
                        ellipse(pos,uEyes.xy-vec2(0.0,faceDim.y*.046),
                            faceDim*vec2(.19,.029))+
                        ellipse(pos,uEyes.zw-vec2(0.0,faceDim.y*.046),
                            faceDim*vec2(.19,.029)));
                    rgb=mix(rgb,vec3(.12,.08,.08),lashes*uMakeup1.x*.45);
                    float brows=min(1.0,
                        ellipse(pos,uBrows.xy,faceDim*vec2(.19,.038))+
                        ellipse(pos,uBrows.zw,faceDim*vec2(.19,.038)));
                    rgb=mix(rgb,vec3(.25,.18,.16),brows*uMakeup1.y*.48);

                    if(clarity>.001){
                        vec3 a=source(vUv+vec2(uPixel.x,0.0));
                        vec3 b=source(vUv-vec2(uPixel.x,0.0));
                        vec3 c=source(vUv+vec2(0.0,uPixel.y));
                        vec3 d=source(vUv-vec2(0.0,uPixel.y));
                        rgb+=(original-(a+b+c+d)*.25)*clarity*.34*
                            min(1.0,eyeZone*1.3);
                    }
                }
                if(hd>.001){
                    vec3 l=source(vUv+vec2(uPixel.x,0.0));
                    vec3 r=source(vUv-vec2(uPixel.x,0.0));
                    rgb+=(original-(l+r)*.5)*hd*.25;
                }
                rgb+=vec3(uColor.w,0.0,-uColor.w);
                rgb=(rgb-.5)*uColor.y+.5+uColor.x;
                float gray=dot(rgb,vec3(.2126,.7152,.0722));
                rgb=mix(vec3(gray),rgb,uColor.z);
                // uStyle: HD/Basic/iPhone photograph mood (not the proprietary ISP).
                rgb=rgb*vec3(uStyle.x,uStyle.y,uStyle.z)+vec3(uStyle.w);
                gl_FragColor=vec4(clamp(rgb,0.0,1.0),1.0);
            }
            """;
    private BeautyShader(){}
}
