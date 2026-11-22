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
    float aspect = size.x / size.y;
    float phase = time * 0.0000698;
    float3 hue = mix(float3(tint.rgb), float3(accent.rgb), energy * 0.22);
    float3 topLight = mix(float3(0.91, 0.97, 1.0), hue, 0.11);
    float3 bottomLight = mix(float3(0.31, 0.59, 0.76), hue, 0.30);
    float3 topDark = mix(float3(0.025, 0.075, 0.12), hue * 0.22, 0.26);
    float3 bottomDark = float3(0.008, 0.018, 0.03);
    float3 color = mix(mix(topLight, bottomLight, uv.y), mix(topDark, bottomDark, uv.y), dark);

    float2 glowPoint = float2(0.51 + tilt.x * 0.015 + 0.008 * sin(phase), 0.36 + tilt.y * 0.01);
    float2 glowDelta = float2((uv.x - glowPoint.x) * aspect, uv.y - glowPoint.y);
    float glow = exp(-dot(glowDelta, glowDelta) * 5.0);
    color += hue * glow * mix(0.045, 0.075, dark);

    float horizon = 0.61 + 0.025 * sin(uv.x * 3.0 + 0.3) - 0.014 * (uv.x - 0.5);
    float band = exp(-pow((uv.y - horizon) / 0.012, 2.0));
    color += mix(float3(0.82, 0.95, 1.0), hue, 0.40) * band * mix(0.11, 0.045, dark);
    float shelf = smoothstep(horizon - 0.018, horizon + 0.06, uv.y);
    color = mix(color, mix(color, hue, mix(0.055, 0.025, dark)), shelf * 0.35);

    float orbitY = (uv.y - 0.43) * 2.0;
    float orbitX = (uv.x - 0.5) * aspect / 0.78;
    float orbit = abs(orbitY * orbitY + orbitX * orbitX - 1.0);
    float edge = exp(-orbit * 190.0) * smoothstep(0.18, 0.33, uv.y) * (1.0 - smoothstep(0.72, 0.86, uv.y));
    color += float3(0.86, 0.97, 1.0) * edge * mix(0.10, 0.035, dark);

    float sx = 0.18 + 0.64 * (0.5 + 0.5 * sin(phase));
    float sheen = exp(-pow((uv.x - sx) * 5.5 + (uv.y - 0.21) * 2.2, 2.0));
    color += float3(0.88, 0.97, 1.0) * sheen * (1.0 - smoothstep(0.12, 0.36, uv.y)) * mix(0.035, 0.018, dark);

    float vignette = smoothstep(1.18, 0.34, length((uv - 0.5) * float2(1.0, 1.1)));
    color *= mix(0.90, 1.0, vignette);
    color += hue * pulse * 0.012;
    return half4(half3(clamp(color, 0.0, 1.0)), 1.0);
}
"""
}
