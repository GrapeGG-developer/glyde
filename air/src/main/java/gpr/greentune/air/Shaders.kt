package gpr.greentune.air

object Shaders {
    const val BACKGROUND = """
uniform float2 size;
uniform float time;
uniform float2 tilt;
uniform half4 tint;
uniform half4 accent;
uniform float dark;
uniform float energy;
uniform float pulse;

half4 main(float2 fc) {
    float2 uv = fc / size;
    float asp = size.x / size.y;
    float ph = time * 6.2831853 / 3600.0;
    float3 tc = float3(tint.rgb);
    float3 ac = float3(accent.rgb);
    float3 hue = mix(tc, ac, energy * 0.6);

    float3 topL = mix(float3(0.86, 0.95, 1.0), hue, 0.18);
    float3 botL = mix(float3(0.20, 0.46, 0.68), hue * 0.85, 0.40);
    float3 topD = mix(float3(0.03, 0.09, 0.15), hue * 0.30, 0.35);
    float3 botD = float3(0.005, 0.015, 0.03);
    float3 col = mix(mix(topL, botL, uv.y), mix(topD, botD, uv.y), dark);

    float x = uv.x * asp + tilt.x * 0.02;
    float3 light = float3(0.0);
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float y = 0.56 + fi * 0.1 - tilt.y * 0.01
            + 0.07 * sin(x * (1.1 + fi * 0.3) + ph * (40.0 + fi * 15.0) + fi * 2.0)
            - 0.22 * (uv.x - 0.5);
        float w = 0.06 + 0.03 * sin(x * 0.9 + ph * (25.0 + fi * 10.0) + fi);
        float d = uv.y - y;
        float band = exp(-d * d / (w * w));
        float sheen = exp(-abs(d) / (w * 0.12));
        light += mix(hue, float3(0.85, 0.97, 1.0), 0.35 + fi * 0.15) * (band * 0.22 + sheen * 0.16);
    }
    col += light * mix(0.55, 0.8, dark) * (0.85 + 0.25 * energy + 0.06 * pulse);

    float glare = smoothstep(0.45, 0.0, uv.y) * (0.5 + 0.5 * sin(uv.x * 2.4 + ph * 30.0));
    col += mix(float3(1.0), hue, 0.5) * glare * mix(0.06, 0.05, dark);

    float2 gp = float2((uv.x - 0.5) * asp, uv.y - 0.42);
    col += hue * exp(-dot(gp, gp) * 6.0) * (0.03 + 0.10 * energy);

    float vig = smoothstep(1.35, 0.35, length((uv - 0.5) * float2(1.0, 1.2)));
    col *= mix(0.78, 1.0, vig);
    float n = fract(sin(dot(fc, float2(12.9898, 78.233))) * 43758.5453);
    col += (n - 0.5) / 255.0;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""
}
