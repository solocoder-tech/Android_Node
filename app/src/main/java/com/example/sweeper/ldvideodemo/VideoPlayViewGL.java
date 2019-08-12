
package com.example.sweeper.ldvideodemo;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;


public class VideoPlayViewGL extends GLSurfaceView {

	private MyRenderer m_Renderer = null;

	public VideoPlayViewGL(Context ctx) {
		super(ctx);

		Log.d("DevExtend", "VideoPlayViewGL.VideoPlayViewGL");

		this.setFocusableInTouchMode(true);

		this.setEGLContextClientVersion(2);

		this.setEGLConfigChooser(8, 8, 8, 8, 0, 0);
		this.setDebugFlags(DEBUG_CHECK_GL_ERROR);
		
		m_Renderer = new MyRenderer();
		this.setRenderer(m_Renderer);
		this.setRenderMode(RENDERMODE_WHEN_DIRTY);
		

		Log.d("DevExtend", "VideoPlayViewGL.VideoPlayViewGL end");
	}

	public void DrawBitmap(byte[] byData, int iPosX, int iPosY, int iWidth, int iHeight, int iFillMode) {
		m_Renderer.DrawBitmap(byData, iPosX, iPosY, iWidth, iHeight, iFillMode);
		requestRender();
	}


	public void DrawClean() {
		m_Renderer.DrawClean();
		requestRender();
	}
}


///-------------------------------------------------------------------------------------
// Renderer.
class MyRenderer implements GLSurfaceView.Renderer {

	// Videp bitmap mode
	private static final int VIDEO_BITMAP_DstInSrc = 0;
	private static final int VIDEO_BITMAP_SrcInDst = 1;
	private static final int VIDEO_BITMAP_SrcFitDst = 2;

	// Board member.
	private int m_iWndWidth = 0;
	private int m_iWndHeight = 0;
	
	private int m_iTexture = -1;

	private Object m_sDraw = new Object();
	private ByteBuffer m_byBuf = null;
	private int m_iDrawWidth = 0;
	private int m_iDrawHeight = 0;
	private int m_iDrawFillMode = VIDEO_BITMAP_DstInSrc;

	private String m_sVertexShader = "";
	private String m_sFragmentShader = "";
	
    private int mProgram;
    private int glHPosition;
    private int glHTexture;
    private int glHCoordinate;
    private int glHMatrix;
    private FloatBuffer bPos;
    private FloatBuffer bCoord;
    private float[] mViewMatrix = new float[16];
    private float[] mProjectMatrix = new float[16];
    private float[] mMVPMatrix = new float[16];

	private final float[] sPos = {
		-1.0f,  1.0f,    //���Ͻ�
		-1.0f, -1.0f,    //���½�
		1.0f,  1.0f,    //���Ͻ�
		1.0f, -1.0f     //���½�
    };
	
	private final float[] sCoord = {
		0.0f, 0.0f,
		0.0f, 1.0f,
		1.0f, 0.0f,
		1.0f, 1.0f,
    };
	
    public MyRenderer() {
		super();

		m_sVertexShader = "attribute vec4 vPosition;\n"
			+ "attribute vec2 vCoordinate;\n"
			+ "uniform mat4 vMatrix;\n"
			+ "varying vec2 aCoordinate;\n"
			+ "void main(){\n"
			+ "    gl_Position=vMatrix*vPosition;\n"
			+ "    aCoordinate=vCoordinate;\n"
			+ "}";
		
		m_sFragmentShader = "precision mediump float;\n"
			+ "uniform sampler2D vTexture;\n"
			+ "varying vec2 aCoordinate;\n"
			+ "void main(){\n"
			+ "    gl_FragColor=texture2D(vTexture,aCoordinate);\n"
			+ "}";

        ByteBuffer bb = ByteBuffer.allocateDirect(sPos.length * 4);
        bb.order(ByteOrder.nativeOrder());
        bPos = bb.asFloatBuffer();
        bPos.put(sPos);
        bPos.position(0);

        ByteBuffer cc = ByteBuffer.allocateDirect(sCoord.length * 4);
        cc.order(ByteOrder.nativeOrder());
        bCoord = cc.asFloatBuffer();
        bCoord.put(sCoord);
        bCoord.position(0);

        Log.d("DevExtend", "MyRenderer.MyRenderer");
	}
	
	public void DrawBitmap(byte[] byData, int iPosX, int iPosY, int iWidth, int iHeight, int iFillMode) {
		try {
			synchronized(m_sDraw) {
				m_byBuf = ByteBuffer.wrap(byData);
				m_iDrawWidth = iWidth;
				m_iDrawHeight = iHeight;
				m_iDrawFillMode = iFillMode;
			}
		}
		catch (Exception ex) {
			Log.d("DevExtend", "MyRenderer.DrawBitmap, ex=" + ex.toString());
		}
	}
	
	public void DrawClean() {
		try {
			synchronized(m_sDraw) {
				m_byBuf = null;
			}
		}
		catch (Exception ex) {
			Log.d("DevExtend", "MyRenderer.DrawClean, ex=" + ex.toString());
		}		
	}

	@Override
	public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl) {
		
		try {
			synchronized(m_sDraw) {

		        int iErr;
				if (m_byBuf != null) {
					
			        float sWH = (float)m_iDrawWidth / (float)m_iDrawHeight;
			        float sWidthHeight = m_iWndWidth / (float)m_iWndHeight;

					if (m_iDrawFillMode == VIDEO_BITMAP_DstInSrc) {
			            if (sWH > sWidthHeight) {
			                Matrix.orthoM(mProjectMatrix, 0, -(sWidthHeight / sWH), (sWidthHeight / sWH), -1, 1, 3, 5);
			            }
			            else {
			                Matrix.orthoM(mProjectMatrix, 0, -1, 1, -(sWH / sWidthHeight), (sWH / sWidthHeight), 3, 5);
			            }
					}
					else if (m_iDrawFillMode == VIDEO_BITMAP_SrcInDst) {
			            if (sWH > sWidthHeight) {
			                Matrix.orthoM(mProjectMatrix, 0,  -1, 1, -(sWH / sWidthHeight), (sWH / sWidthHeight), 3, 5);
			            }
			            else {
			                Matrix.orthoM(mProjectMatrix, 0, -(sWidthHeight / sWH), (sWidthHeight / sWH), -1, 1, 3, 5);
			            }
				    }
				    else {
		                Matrix.orthoM(mProjectMatrix, 0, -1, 1, -1, 1, 3, 5);
				    }

			        Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 5.0f, 0f, 0f, 0f, 0f, 1.0f, 0.0f);
			        Matrix.multiplyMM(mMVPMatrix, 0, mProjectMatrix, 0, mViewMatrix, 0);

			        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
			        GLES20.glUseProgram(mProgram);
			        GLES20.glUniformMatrix4fv(glHMatrix, 1, false, mMVPMatrix, 0);
			        GLES20.glEnableVertexAttribArray(glHPosition);
			        GLES20.glEnableVertexAttribArray(glHCoordinate);
			        GLES20.glUniform1i(glHTexture, 0);

			        createTexture();

			        GLES20.glVertexAttribPointer(glHPosition, 2, GLES20.GL_FLOAT, false, 0, bPos);
			        GLES20.glVertexAttribPointer(glHCoordinate, 2, GLES20.GL_FLOAT, false, 0, bCoord);
			        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
			    }
				else {
					GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
					if ((iErr = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
						Log.d("DevExtend", "MyRenderer.onDrawFrame: glClearColor, iErr=" + iErr);
					}

					GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

					Log.d("DevExtend", "MyRenderer.onDrawFrame clear");
				}
			}
		}
		catch (Exception ex) {
			Log.d("DevExtend", "MyRenderer.onDrawFrame, ex=" + ex.toString());
		}
	}

	@Override
	public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl, int width, int height) {
		Log.d("DevExtend", "MyRenderer.onSurfaceChanged");

		m_iWndWidth = width;
		m_iWndHeight = height;

		int iErr;

		GLES20.glViewport(0, 0, width, height);
		if ((iErr = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
			Log.d("DevExtend", "MyRenderer.onSurfaceChanged: glViewport, iErr=" + iErr);
		}
	}

	@Override
	public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 arg0,
		javax.microedition.khronos.egl.EGLConfig arg1)
	{
		// TODO Auto-generated method stub
		Log.d("DevExtend", "MyRenderer.onSurfaceCreated");


        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
        GLES20.glEnable(GLES20.GL_TEXTURE_2D);
 
        mProgram = ShaderUtils.createProgram(m_sVertexShader, m_sFragmentShader);
        glHPosition = GLES20.glGetAttribLocation(mProgram, "vPosition");
        glHCoordinate = GLES20.glGetAttribLocation(mProgram, "vCoordinate");
        glHTexture = GLES20.glGetUniformLocation(mProgram, "vTexture");
        glHMatrix = GLES20.glGetUniformLocation(mProgram, "vMatrix");
	}

    private void createTexture() {
    	int iErr;

		if (m_iTexture < 0) {
			int iTexture[] = new int[1];
			GLES20.glGenTextures(1, iTexture, 0);
			if ((iErr = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
				Log.d("DevExtend", "MyRenderer.onDrawFrame: glGenTextures, iErr=" + iErr);
			}
			m_iTexture = iTexture[0];
			Log.d("DevExtend", "MyRenderer.onDrawFrame, m_iTexture=" + m_iTexture);
		}

		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, m_iTexture);
		if ((iErr = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
			Log.d("DevExtend", "MyRenderer.onDrawFrame: glBindTexture, iErr=" + iErr);
		}

		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
		if ((iErr = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
			Log.d("DevExtend", "MyRenderer.onDrawFrame: glTexParameteri, iErr=" + iErr);
		}
		
		GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB, m_iDrawWidth,
			m_iDrawHeight, 0, GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, m_byBuf);
		if ((iErr = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
			Log.d("DevExtend", "MyRenderer.onDrawFrame: glTexImage2D, iErr=" + iErr);
		}
    }	
}

class ShaderUtils {

    public static int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        if (shader != 0){  
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] compiled = new int[1];  
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled,0);
            if (compiled[0] == 0){  
        		Log.d("DevExtend", "ShaderUtil.loadShader: Could not compile shader" + shaderType + ":");
                Log.d("DevExtend", "ShaderUtil.loadShader: " + GLES20.glGetShaderInfoLog(shader));
                shader = 0;  
            }
        }
        else {
    		Log.d("DevExtend", "ShaderUtil.loadShader: glCreateShader failed");
        }
        return shader;  
    }

    public static int createProgram(String vertexSource, String fragmentSource){
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) {  
    		Log.d("DevExtend", "ShaderUtil.createProgram: loadShader ��GL_VERTEX_SHADER�� failed");
            return 0;  
        }

        int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (pixelShader == 0) {  
    		Log.d("DevExtend", "ShaderUtil.createProgram: loadShader ��GL_FRAGMENT_SHADER�� failed");
            return 0;  
        }  

        int program = GLES20.glCreateProgram();
        if (program != 0) {  
            GLES20.glAttachShader(program, vertexShader);
            checkGlError("glAttachShader");

            GLES20.glAttachShader(program, pixelShader);
            checkGlError("glAttachShader");

            GLES20.glLinkProgram(program);

            int[] linkStatus = new int[1];  
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GLES20.GL_TRUE) {
        		Log.d("DevExtend", "ShaderUtil.createProgram: could not link program::");
                Log.d("DevExtend", "ShaderUtil.createProgram: " + GLES20.glGetProgramInfoLog(program));
                GLES20.glDeleteProgram(program);
                program = 0;  
            }  
        }
        else {
    		Log.d("DevExtend", "ShaderUtil.createProgram: glCreateProgram failed");
        }

        return program;  
    }

    public static void checkGlError(String op) {
        int error;  
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
    		Log.d("DevExtend", op + ":  glError" + error);
        }  
    }  
} 

