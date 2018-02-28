package com.jiangdg.usbcamera;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Environment;

import com.jiangdg.libusbcamera.R;
import com.serenegiant.usb.DeviceFilter;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.common.AbstractUVCCameraHandler;
import com.serenegiant.usb.common.UVCCameraHandler;
import com.serenegiant.usb.encoder.RecordParams;
import com.serenegiant.usb.widget.CameraViewInterface;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;

/** UVCCamera Helper class
 *
 * Created by jiangdongguo on 2017/9/30.
 */

public class UVCCameraHelper {
    public static final String ROOT_PATH = Environment.getExternalStorageDirectory().getAbsolutePath()
            + File.separator;
    public static final String SUFFIX_PNG = ".png";
    public static final String SUFFIX_MP4 = ".mp4";
    private static final String TAG = "UVCCameraHelper";
    private int previewWidth = 640;
    private int previewHeight = 480;
    public static int MODE_BRIGHTNESS = UVCCamera.PU_BRIGHTNESS;
    public static int MODE_CONTRAST = UVCCamera.PU_CONTRAST;
    //0-YUYV，1-MJPEG
    private static final int PREVIEW_FORMAT = 0;

    private static UVCCameraHelper mCameraHelper;
    // USB Manager
    private USBMonitor mUSBMonitor;
    // Camera Handler
    private UVCCameraHandler mCameraHandler;
    private USBMonitor.UsbControlBlock mCtrlBlock;

    private WeakReference<Activity> mActivityWrf;
    private WeakReference<CameraViewInterface> mCamViewWrf;

    private UVCCameraHelper() {
    }

    public static UVCCameraHelper getInstance() {
        if (mCameraHelper == null) {
            mCameraHelper = new UVCCameraHelper();
        }
        return mCameraHelper;
    }

    public void closeCamera() {
        if (mCameraHandler != null) {
            mCameraHandler.close();
        }
    }

    public interface OnMyDevConnectListener {
        void onAttachDev(UsbDevice device);

        void onDettachDev(UsbDevice device);

        void onConnectDev(UsbDevice device, boolean isConnected);

        void onDisConnectDev(UsbDevice device);
    }

    public interface OnPreviewListener {
        void onPreviewResult(boolean isSuccess);
    }

    public void initUSBMonitor(Activity activity, CameraViewInterface cameraView, final OnMyDevConnectListener listener) {
        this.mActivityWrf = new WeakReference<>(activity);
        this.mCamViewWrf = new WeakReference<>(cameraView);
        mUSBMonitor = new USBMonitor(activity.getApplicationContext(), new USBMonitor.OnDeviceConnectListener() {

            // called by checking usb device
            // do request device permission
            @Override
            public void onAttach(UsbDevice device) {
                if (listener != null) {
                    listener.onAttachDev(device);
                }
            }

            // called by taking out usb device
            // do close camera
            @Override
            public void onDettach(UsbDevice device) {
                if (listener != null) {
                    listener.onDettachDev(device);
                }
            }

            // called by connect to usb camera
            // do open camera,start previewing
            @Override
            public void onConnect(final UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock, boolean createNew) {
                mCtrlBlock = ctrlBlock;
                openCamera(ctrlBlock);
                startPreview(mCamViewWrf.get(), new AbstractUVCCameraHandler.OnPreViewResultListener() {
                    @Override
                    public void onPreviewResult(boolean isConnected) {
                        if (listener != null) {
                            listener.onConnectDev(device, isConnected);
                        }
                    }
                });
            }

            // called by disconnect to usb camera
            // do nothing
            @Override
            public void onDisconnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {
                if (listener != null) {
                    listener.onDisConnectDev(device);
                }
            }

            @Override
            public void onCancel(UsbDevice device) {
            }
        });

        createUVCCamera();
    }

    public void createUVCCamera() {
        if (mCamViewWrf.get() == null)
            throw new NullPointerException("CameraViewInterface cannot be null!");

        // release resources for initializing camera handler
        if (mCameraHandler != null) {
            mCameraHandler.release();
            mCameraHandler = null;
        }
        // initialize camera handler
//        cameraView.setAspectRatio(previewWidth / (float)previewHeight);
        mCameraHandler = UVCCameraHandler.createHandler(mActivityWrf.get(), mCamViewWrf.get(), 2,
                previewWidth, previewHeight, PREVIEW_FORMAT);
    }

    public void updateResolution(int width, int height, final OnPreviewListener mPreviewListener) {
        if (previewWidth == width && previewHeight == height) {
            return;
        }
        this.previewWidth = width;
        this.previewHeight = height;
        if (mCameraHandler != null) {
            mCameraHandler.release();
            mCameraHandler = null;
        }
//        cameraView.setAspectRatio(previewWidth / (float)previewHeight);
        mCameraHandler = UVCCameraHandler.createHandler(mActivityWrf.get(), mCamViewWrf.get(), 2,
                previewWidth, previewHeight, PREVIEW_FORMAT);
        openCamera(mCtrlBlock);
        startPreview(mCamViewWrf.get(), new AbstractUVCCameraHandler.OnPreViewResultListener() {
            @Override
            public void onPreviewResult(boolean result) {
                if (mPreviewListener != null) {
                    mPreviewListener.onPreviewResult(result);
                }
            }
        });
    }

    public void registerUSB() {
        if (mUSBMonitor != null) {
            mUSBMonitor.register();
        }
    }

    public void unregisterUSB() {
        if (mUSBMonitor != null) {
            mUSBMonitor.unregister();
        }
    }

    public boolean checkSupportFlag(final int flag) {
        return mCameraHandler != null && mCameraHandler.checkSupportFlag(flag);
    }

    public int getModelValue(final int flag) {
        return mCameraHandler != null ? mCameraHandler.getValue(flag) : 0;
    }

    public int setModelValue(final int flag, final int value) {
        return mCameraHandler != null ? mCameraHandler.setValue(flag, value) : 0;
    }

    public int resetModelValue(final int flag) {
        return mCameraHandler != null ? mCameraHandler.resetValue(flag) : 0;
    }

    public void requestPermission(int index) {
        List<UsbDevice> devList = getUsbDeviceList();
        if (devList == null || devList.size() == 0) {
            return;
        }
        int count = devList.size();
        if (index >= count)
            new IllegalArgumentException("index illegal,should be < devList.size()");
        if (mUSBMonitor != null) {
            mUSBMonitor.requestPermission(getUsbDeviceList().get(index));
        }
    }

    public int getUsbDeviceCount() {
        List<UsbDevice> devList = getUsbDeviceList();
        if (devList == null || devList.size() == 0) {
            return 0;
        }
        return devList.size();
    }

    public List<UsbDevice> getUsbDeviceList() {
        List<DeviceFilter> deviceFilters = DeviceFilter
                .getDeviceFilters(mActivityWrf.get().getApplicationContext(), R.xml.device_filter);
        if (mUSBMonitor == null || deviceFilters == null)
            return null;
        return mUSBMonitor.getDeviceList(deviceFilters.get(0));
    }

    public void capturePicture(String savePath, AbstractUVCCameraHandler.OnCaptureListener listener) {
        if (mCameraHandler != null && mCameraHandler.isOpened()) {
            mCameraHandler.captureStill(savePath, listener);
        }
    }

    public void startRecording(RecordParams params, AbstractUVCCameraHandler.OnEncodeResultListener listener) {
        if (mCameraHandler != null && !isRecording()) {
            mCameraHandler.startRecording(params, listener);
        }
    }

    public void stopRecording() {
        if (mCameraHandler != null && isRecording()) {
            mCameraHandler.stopRecording();
        }
    }

    public boolean isRecording() {
        if (mCameraHandler != null) {
            return mCameraHandler.isRecording();
        }
        return false;
    }

    public boolean isCameraOpened() {
        if (mCameraHandler != null) {
            return mCameraHandler.isOpened();
        }
        return false;
    }

    public void release() {
        mCamViewWrf.clear();
        mActivityWrf.clear();
        if (mCameraHandler != null) {
            mCameraHandler.release();
            mCameraHandler = null;
        }
        if (mUSBMonitor != null) {
            mUSBMonitor.destroy();
            mUSBMonitor = null;
        }
    }

    public USBMonitor getUSBMonitor() {
        return mUSBMonitor;
    }

    private void openCamera(USBMonitor.UsbControlBlock ctrlBlock) {
        if (mCameraHandler != null) {
            mCameraHandler.open(ctrlBlock);
        }
    }

    public void startPreview(CameraViewInterface cameraView, AbstractUVCCameraHandler.OnPreViewResultListener mPreviewListener) {
        SurfaceTexture st = cameraView.getSurfaceTexture();
        if (mCameraHandler != null) {
            mCameraHandler.startPreview(st, mPreviewListener);
        }
    }

    public void stopPreview() {
        if (mCameraHandler != null) {
            mCameraHandler.stopPreview();
        }
    }

    public void startCameraFoucs() {
        if (mCameraHandler != null) {
            mCameraHandler.startCameraFoucs();
        }
    }

    public List<Size> getSupportedPreviewSizes() {
        if (mCameraHandler == null)
            return null;
        return mCameraHandler.getSupportedPreviewSizes();
    }
}
