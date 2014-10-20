package net.osmand.core.samples.android.sample1;

import android.opengl.EGL14;
import android.opengl.GLSurfaceView;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

import net.osmand.core.jni.*;
import net.osmand.core.android.*;

public class MainActivity extends ActionBarActivity {
    static {
        try {
            System.loadLibrary("gnustl_shared");
        }
        catch( UnsatisfiedLinkError e ) {
            System.err.println("Failed to load 'gnustl_shared':" + e);
            System.exit(0);
        }
        try {
            System.loadLibrary("Qt5Core");
        }
        catch( UnsatisfiedLinkError e ) {
            System.err.println("Failed to load 'Qt5Core':" + e);
            System.exit(0);
        }
        try {
            System.loadLibrary("Qt5Network");
        }
        catch( UnsatisfiedLinkError e ) {
            System.err.println("Failed to load 'Qt5Network':" + e);
            System.exit(0);
        }
        try {
            System.loadLibrary("Qt5Sql");
        }
        catch( UnsatisfiedLinkError e ) {
            System.err.println("Failed to load 'Qt5Sql':" + e);
            System.exit(0);
        }
        try {
            System.loadLibrary("OsmAndCoreWithJNI");
        }
        catch( UnsatisfiedLinkError e ) {
            System.err.println("Failed to load 'OsmAndCoreWithJNI':" + e);
            System.exit(0);
        }
    }

    private static final String TAG = "OsmAndCoreSample";

    private CoreResourcesFromAndroidAssets _coreResources;

    private float _displayDensityFactor;
    private int _referenceTileSize;
    private int _rasterTileSize;
    private IMapStylesCollection _mapStylesCollection;
    private ResolvedMapStyle _mapStyle;
    private ObfsCollection _obfsCollection;
    private MapPresentationEnvironment _mapPresentationEnvironment;
    private Primitiviser _primitiviser;
    private BinaryMapDataProvider _binaryMapDataProvider;
    private BinaryMapPrimitivesProvider _binaryMapPrimitivesProvider;
    private BinaryMapStaticSymbolsProvider _binaryMapStaticSymbolsProvider;
    private BinaryMapRasterLayerProvider _binaryMapRasterLayerProvider;
    private IMapRenderer _mapRenderer;
    private GpuWorkerThreadPrologue _gpuWorkerThreadPrologue;
    private GpuWorkerThreadEpilogue _gpuWorkerThreadEpilogue;
    private RenderRequestCallback _renderRequestCallback;
    private QIODeviceLogSink _fileLogSink;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get device display density factor
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        _displayDensityFactor = displayMetrics.densityDpi / 160.0f;
        _referenceTileSize = (int)(256 * _displayDensityFactor);
        _rasterTileSize = Integer.highestOneBit(_referenceTileSize - 1) * 2;
        Log.i(TAG, "displayDensityFactor = " + _displayDensityFactor);
        Log.i(TAG, "referenceTileSize = " + _referenceTileSize);
        Log.i(TAG, "rasterTileSize = " + _rasterTileSize);

        Log.i(TAG, "Initializing core...");
        _coreResources = CoreResourcesFromAndroidAssets.loadFromCurrentApplication(this);
        OsmAndCore.InitializeCore(_coreResources.instantiateProxy());

        _fileLogSink = QIODeviceLogSink.createFileLogSink(Environment.getExternalStorageDirectory() + "/osmand/osmandcore.log");
        Logger.get().addLogSink(_fileLogSink);

        Log.i(TAG, "Going to resolve default embedded style...");
        _mapStylesCollection = new MapStylesCollection();
        _mapStyle = _mapStylesCollection.getResolvedStyleByName("default");
        if (_mapStyle == null)
        {
            Log.e(TAG, "Failed to resolve style 'default'");
            System.exit(0);
        }

        Log.i(TAG, "Going to prepare OBFs collection");
        _obfsCollection = new ObfsCollection();
        Log.i(TAG, "Will load OBFs from " + Environment.getExternalStorageDirectory() + "/osmand");
        _obfsCollection.addDirectory(Environment.getExternalStorageDirectory() + "/osmand", false);

        Log.i(TAG, "Going to prepare all resources for renderer");
        _mapPresentationEnvironment = new MapPresentationEnvironment(
                _mapStyle,
                _displayDensityFactor,
                "en"); //TODO: here should be current locale
        //mapPresentationEnvironment->setSettings(configuration.styleSettings);
        _primitiviser = new Primitiviser(
                _mapPresentationEnvironment);
        _binaryMapDataProvider = new BinaryMapDataProvider(
                _obfsCollection);
        _binaryMapPrimitivesProvider = new BinaryMapPrimitivesProvider(
                _binaryMapDataProvider,
                _primitiviser,
                _rasterTileSize);
        _binaryMapStaticSymbolsProvider = new BinaryMapStaticSymbolsProvider(
                _binaryMapPrimitivesProvider,
                _rasterTileSize);
        _binaryMapRasterLayerProvider = new BinaryMapRasterLayerProvider_Software(
                _binaryMapPrimitivesProvider);

        Log.i(TAG, "Going to create renderer");
        _mapRenderer = OsmAndCore.createMapRenderer(MapRendererClass.AtlasMapRenderer_OpenGLES2);
        if (_mapRenderer == null)
        {
            Log.e(TAG, "Failed to create map renderer 'AtlasMapRenderer_OpenGLES2'");
            System.exit(0);
        }

        AtlasMapRendererConfiguration atlasRendererConfiguration = AtlasMapRendererConfiguration.Casts.upcastFrom(_mapRenderer.getConfiguration());
        atlasRendererConfiguration.setReferenceTileSizeOnScreenInPixels(_referenceTileSize);
        _mapRenderer.setConfiguration(AtlasMapRendererConfiguration.Casts.downcastTo_MapRendererConfiguration(atlasRendererConfiguration));

        _mapRenderer.addSymbolsProvider(_binaryMapStaticSymbolsProvider);
        _mapRenderer.setAzimuth(0.0f);
        _mapRenderer.setElevationAngle(35.0f);

        _mapRenderer.setTarget(new PointI(
                1102430866,
                704978668));
        _mapRenderer.setZoom(10.0f);
        /*
        IMapRasterLayerProvider mapnik = OnlineTileSources.getBuiltIn().createProviderFor("Mapnik (OsmAnd)");
        if (mapnik == null)
            Log.e(TAG, "Failed to create mapnik");
        */
        _mapRenderer.setMapLayerProvider(0, _binaryMapRasterLayerProvider);

        _glSurfaceView = (GLSurfaceView) findViewById(R.id.glSurfaceView);
        //TODO:_glSurfaceView.setPreserveEGLContextOnPause(true);
        _glSurfaceView.setEGLContextClientVersion(2);
        _glSurfaceView.setEGLConfigChooser(true);
        _glSurfaceView.setEGLContextFactory(new EGLContextFactory());
        _glSurfaceView.setRenderer(new Renderer());
        _glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    private GLSurfaceView _glSurfaceView;

    @Override
    protected void onPause() {
        super.onPause();
        _glSurfaceView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        _glSurfaceView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (_mapStylesCollection != null) {
            _mapStylesCollection.delete();
            _mapStylesCollection = null;
        }

        if (_mapStyle != null) {
            _mapStyle.delete();
            _mapStyle = null;
        }

        if (_obfsCollection != null) {
            _obfsCollection.delete();
            _obfsCollection = null;
        }

        if (_mapPresentationEnvironment != null) {
            _mapPresentationEnvironment.delete();
            _mapPresentationEnvironment = null;
        }

        if (_primitiviser != null) {
            _primitiviser.delete();
            _primitiviser = null;
        }

        if (_binaryMapDataProvider != null) {
            _binaryMapDataProvider.delete();
            _binaryMapDataProvider = null;
        }

        if (_binaryMapPrimitivesProvider != null) {
            _binaryMapPrimitivesProvider.delete();
            _binaryMapPrimitivesProvider = null;
        }

        if (_binaryMapStaticSymbolsProvider != null) {
            _binaryMapStaticSymbolsProvider.delete();
            _binaryMapStaticSymbolsProvider = null;
        }

        if (_binaryMapRasterLayerProvider != null) {
            _binaryMapRasterLayerProvider.delete();
            _binaryMapRasterLayerProvider = null;
        }

        if (_mapRenderer != null) {
            _mapRenderer.delete();
            _mapRenderer = null;
        }

        OsmAndCore.ReleaseCore();

        super.onDestroy();
    }

    private class RenderRequestCallback extends MapRendererSetupOptions.IFrameUpdateRequestCallback {
        @Override
        public void method(IMapRenderer mapRenderer) {
            _glSurfaceView.requestRender();
        }
    }

    private class GpuWorkerThreadPrologue extends MapRendererSetupOptions.IGpuWorkerThreadPrologue {
        public GpuWorkerThreadPrologue(EGL10 egl, EGLDisplay eglDisplay, EGLContext context, EGLSurface surface) {
            _egl = egl;
            _eglDisplay = eglDisplay;
            _context = context;
            _eglSurface = surface;
        }

        private final EGL10 _egl;
        private final EGLDisplay _eglDisplay;
        private final EGLContext _context;
        private final EGLSurface _eglSurface;

        @Override
        public void method(IMapRenderer mapRenderer) {
            try {
                if (!_egl.eglMakeCurrent(_eglDisplay, _eglSurface, _eglSurface, _context))
                    Log.e(TAG, "Failed to set GPU worker context active: " + _egl.eglGetError());
            } catch (Exception e) {
                Log.e(TAG, "Failed to set GPU worker context active", e);
            }
        }
    }

    private class GpuWorkerThreadEpilogue extends MapRendererSetupOptions.IGpuWorkerThreadEpilogue {
        public GpuWorkerThreadEpilogue(EGL10 egl) {
            _egl = egl;
        }

        private final EGL10 _egl;

        @Override
        public void method(IMapRenderer mapRenderer) {
            try {
                if (!_egl.eglWaitGL())
                    Log.e(TAG, "Failed to wait for GPU worker context: " + _egl.eglGetError());
            } catch (Exception e) {
                Log.e(TAG, "Failed to wait for GPU worker context", e);
            }
        }
    }

    private class EGLContextFactory implements GLSurfaceView.EGLContextFactory {
        private EGLContext _gpuWorkerContext;
        private EGLSurface _gpuWorkerFakeSurface;

        public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig eglConfig) {
            final String eglExtensions = egl.eglQueryString(display, EGL10.EGL_EXTENSIONS);
            Log.i(TAG, "EGL extensions: " + eglExtensions);
            final String eglVersion = egl.eglQueryString(display, EGL10.EGL_VERSION);
            Log.i(TAG, "EGL version: " + eglVersion);

            Log.i(TAG, "Creating main context...");
            final int[] contextAttribList = {
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                    EGL10.EGL_NONE };

            EGLContext mainContext = null;
            try {
                mainContext = egl.eglCreateContext(display, eglConfig, EGL10.EGL_NO_CONTEXT, contextAttribList);
            } catch (Exception e) {
                Log.e(TAG, "Failed to create main context", e);
            }
            if (mainContext == null || mainContext == EGL10.EGL_NO_CONTEXT) {
                Log.e(TAG, "Failed to create main context: " + egl.eglGetError());
                mainContext = null;
                System.exit(0);
            }
            Log.d(TAG, "OpenGLES main context = " + mainContext);

            Log.i(TAG, "Creating GPU worker context...");
            try {
                _gpuWorkerContext = egl.eglCreateContext(
                        display,
                        eglConfig,
                        mainContext,
                        contextAttribList);
            } catch (Exception e) {
                Log.e(TAG, "Failed to create GPU worker context", e);
            }
            if (_gpuWorkerContext == null || _gpuWorkerContext == EGL10.EGL_NO_CONTEXT)
            {
                Log.e(TAG, "Failed to create GPU worker context: " + egl.eglGetError());
                _gpuWorkerContext = null;
            }
            Log.d(TAG, "OpenGLES GPU worker context = " + _gpuWorkerContext);

            if (_gpuWorkerContext != null)
            {
                Log.i(TAG, "Creating GPU worker fake surface...");
                try {
                    final int[] surfaceAttribList = {
                            EGL10.EGL_WIDTH, 1,
                            EGL10.EGL_HEIGHT, 1,
                            EGL10.EGL_NONE };
                    _gpuWorkerFakeSurface = egl.eglCreatePbufferSurface(display, eglConfig, surfaceAttribList);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to create GPU worker fake surface", e);
                }
                if (_gpuWorkerFakeSurface == null || _gpuWorkerFakeSurface == EGL10.EGL_NO_SURFACE)
                {
                    Log.e(TAG, "Failed to create GPU worker fake surface: " + egl.eglGetError());
                    _gpuWorkerFakeSurface = null;
                }
            }

            MapRendererSetupOptions rendererSetupOptions = new MapRendererSetupOptions();
            if (_gpuWorkerContext != null && _gpuWorkerFakeSurface != null) {
                rendererSetupOptions.setGpuWorkerThreadEnabled(true);
                _gpuWorkerThreadPrologue = new GpuWorkerThreadPrologue(egl, display, _gpuWorkerContext, _gpuWorkerFakeSurface);
                rendererSetupOptions.setGpuWorkerThreadPrologue(_gpuWorkerThreadPrologue.getBinding());
                _gpuWorkerThreadEpilogue = new GpuWorkerThreadEpilogue(egl);
                rendererSetupOptions.setGpuWorkerThreadEpilogue(_gpuWorkerThreadEpilogue.getBinding());
            } else {
                rendererSetupOptions.setGpuWorkerThreadEnabled(false);
            }
            _renderRequestCallback = new RenderRequestCallback();
            rendererSetupOptions.setFrameUpdateRequestCallback(_renderRequestCallback.getBinding());
            _mapRenderer.setup(rendererSetupOptions);

            return mainContext;
        }

        public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
            egl.eglDestroyContext(display, context);

            if (_gpuWorkerContext != null) {
                egl.eglDestroyContext(display, _gpuWorkerContext);
                _gpuWorkerContext = null;
            }

            if (_gpuWorkerFakeSurface != null) {
                egl.eglDestroySurface(display, _gpuWorkerFakeSurface);
                _gpuWorkerFakeSurface = null;
            }
        }
    }

    private class Renderer implements GLSurfaceView.Renderer {
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            Log.i(TAG, "onSurfaceCreated");
            if (_mapRenderer.isRenderingInitialized())
                _mapRenderer.releaseRendering();
        }

        public void onSurfaceChanged(GL10 gl, int width, int height) {
            Log.i(TAG, "onSurfaceChanged");
            _mapRenderer.setViewport(new AreaI(0, 0, height, width));
            _mapRenderer.setWindowSize(new PointI(width, height));

            if (!_mapRenderer.isRenderingInitialized())
            {
                if (!_mapRenderer.initializeRendering())
                    Log.e(TAG, "Failed to initialize rendering");
            }
        }

        public void onDrawFrame(GL10 gl) {
            _mapRenderer.update();

            if (_mapRenderer.prepareFrame())
                _mapRenderer.renderFrame();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
