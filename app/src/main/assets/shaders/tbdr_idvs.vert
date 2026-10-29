#version 450
// ARM Mali Index-Driven Vertex Shading (IDVS) Split Stream Layout:
// Stream 0 contains strictly position coordinates so culled triangles never fetch Stream 1.
layout(location = 0) in highp vec3 inPosition; // Binding 0 (Stream 0)
layout(location = 1) in highp vec2 inUV;       // Binding 1 (Stream 1)
layout(location = 2) in mediump vec4 inColor;  // Binding 1 (Stream 1)

layout(push_constant) uniform PushConstants {
    highp mat4 uMVP;
    mediump vec4 uTintAndLight;
    uvec4 uMaterialFlags;
} pc;

layout(location = 0) out highp vec2 vUV;
layout(location = 1) out mediump vec4 vColor;

void main() {
    gl_Position = pc.uMVP * vec4(inPosition, 1.0);
    vUV = inUV;
    vColor = inColor * pc.uTintAndLight;
}
