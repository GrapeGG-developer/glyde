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
    float3 tc = float3(tint.rgb);
    float3 ac = float3(accent.rgb);
    float3 hue = mix(tc, ac, energy * 0.55);
    float t = time * (0.32 + 0.35 * energy + 0.5 * pulse);

    float3 topL = mix(float3(0.82, 0.94, 1.0), tc, 0.22);
    float3 botL = mix(float3(0.16, 0.42, 0.66), tc * 0.8, 0.45);
    float3 topD = mix(float3(0.02, 0.08, 0.14), tc * 0.35, 0.3);
    float3 botD = float3(0.0, 0.012, 0.03);
    float3 col = mix(mix(topL, botL, uv.y), mix(topD, botD, uv.y), dark);

    float x = uv.x * asp + tilt.x * 0.10;
    float3 rib = float3(0.0);
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        float base = 0.50 + fi * 0.09 - tilt.y * 0.04;
        float y = base + 0.10 * sin(x * (1.2 + fi * 0.23) + t * (0.55 + fi * 0.12) + fi * 1.7)
            + 0.045 * sin(x * 3.1 - t * 0.8 + fi * 2.1)
            - 0.20 * (uv.x - 0.5) * (1.0 + fi * 0.18);
        float w = 0.035 + 0.075 * (0.5 + 0.5 * sin(x * 1.6 + t * 0.6 + fi * 2.3));
        float d = abs(uv.y - y);
        float g = exp(-d * d / (w * w)) * 0.42 + exp(-d / (w * 0.06)) * 0.22;
        float3 rc = mix(hue, float3(0.80, 0.96, 1.0), fi / 4.0);
        rib += rc * g * (0.32 + 0.05 * fi);
    }
    col += rib * mix(0.50, 0.70, dark) * (0.8 + 0.6 * pulse);

    float au = 0.5 + 0.5 * sin(uv.x * 3.6 + t * 0.35) * sin(uv.x * 1.7 - t * 0.22 + 1.1);
    col += hue * au * smoothstep(0.55, 0.0, uv.y) * mix(0.10, 0.16, dark) * (0.6 + 0.4 * energy + 0.3 * pulse);

    float glowY = 0.42;
    float2 gp = float2((uv.x - 0.5) * asp, uv.y - glowY);
    col += hue * exp(-dot(gp, gp) * 7.0) * (0.05 + 0.12 * energy + 0.15 * pulse);

    float vig = smoothstep(1.3, 0.3, length((uv - 0.5) * float2(1.0, 1.2)));
    col *= mix(0.72, 1.0, vig);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""
}
