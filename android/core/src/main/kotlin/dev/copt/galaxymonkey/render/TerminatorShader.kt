package dev.copt.galaxymonkey.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.glutils.ShaderProgram

object TerminatorShader {

    private const val VERT = """
attribute vec4 a_position;
attribute vec4 a_color;
attribute vec2 a_texCoord0;
uniform mat4 u_projTrans;
varying vec4 v_color;
varying vec2 v_texCoord0;
void main() {
    v_color = a_color;
    v_texCoord0 = a_texCoord0;
    gl_Position = u_projTrans * a_position;
}
"""

    private const val FRAG = """
#ifdef GL_ES
precision mediump float;
#endif
varying vec4 v_color;
varying vec2 v_texCoord0;
uniform sampler2D u_texture;
uniform float u_sunAngle;
void main() {
    vec4 base = texture2D(u_texture, v_texCoord0);
    vec2 centered = v_texCoord0 - 0.5;
    vec2 sunDir = vec2(cos(u_sunAngle), sin(u_sunAngle));
    float d = dot(normalize(centered + vec2(1e-4)), sunDir);
    float lit = smoothstep(-0.4, 0.4, d);
    gl_FragColor = vec4(base.rgb * mix(0.28, 1.04, lit), base.a) * v_color;
}
"""

    var program: ShaderProgram? = null
        private set
    var compiled = false
        private set

    fun init() {
        ShaderProgram.pedantic = false
        val shader = ShaderProgram(VERT, FRAG)
        if (shader.isCompiled) {
            program = shader
            compiled = true
            Gdx.app.log("TerminatorShader", "compiled OK")
        } else {
            Gdx.app.error("TerminatorShader", "compile failed, falling back to flat shading: ${shader.log}")
            shader.dispose()
            compiled = false
        }
    }

    fun dispose() {
        program?.dispose()
        program = null
        compiled = false
    }

    internal fun sunAngleForPlanet(phase: Float, bodyRotation: Float): Float =
        phase + Math.PI.toFloat() - bodyRotation
}
