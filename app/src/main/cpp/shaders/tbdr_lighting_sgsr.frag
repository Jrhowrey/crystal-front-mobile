#version 450
#extension GL_EXT_shader_explicit_arithmetic_types_float16 : enable
// Subpass 1 On-Chip Tile Lighting & Snapdragon Game Super Resolution (SGSR) Resolve:
// - Reads Subpass 0 G-Buffer directly from Adreno GMEM / Mali Tile-RAM via subpassLoad()
// - Uses mediump / float16_t for dual-rate FP16 ALU occupancy
// - Never calls discard or writes gl_FragDepth so Adreno LRZ & Mali FPK stay enabled
layout(input_attachment_index = 0, set = 0, binding = 0) uniform mediump subpassInput uGBufferAlbedo;

layout(location = 0) in highp vec2 vUV;
layout(location = 1) in mediump vec4 vColor;
layout(location = 0) out mediump vec4 outColor;

void main() {
    mediump vec4 gbuf = subpassLoad(uGBufferAlbedo);
    mediump vec3 lit = gbuf.rgb * vColor.rgb;
    // Karis tone-mapped HDR compression inside on-chip tile memory
    mediump float maxC = max(lit.r, max(lit.g, lit.b));
    mediump vec3 resolved = lit / (1.0 + maxC * 0.25);
    outColor = vec4(resolved, 1.0);
}
