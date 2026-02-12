#version 150

out vec4 fragColor;

void main() {
    // Write mask value (1.0 in red channel)
    fragColor = vec4(1.0, 0.0, 0.0, 1.0);
}
