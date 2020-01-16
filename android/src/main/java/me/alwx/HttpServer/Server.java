package me.alwx.HttpServer;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.Random;

import android.support.annotation.Nullable;
import android.util.Log;

public class Server extends NanoHTTPD {
    private static final String TAG = "HttpServer";
    private static final String SERVER_EVENT_ID = "httpServerResponseReceived";

    private ReactContext reactContext;
    private Map<String, Response> responses;

    public Server(ReactContext context, int port) {
        super(port);
        reactContext = context;
        responses = new HashMap<>();

        Log.d(TAG, "Server started");
    }

    @Override
    public Response serve(IHTTPSession session) {
        Log.d(TAG, "Request received!");

        Random rand = new Random();
        String requestId = String.format("%d:%d", System.currentTimeMillis(), rand.nextInt(1000000));

        WritableMap request;
        try {
            request = fillRequestMap(session, requestId);
        } catch (Exception e) {
            return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.getMessage()
            );
        }

        this.sendEvent(reactContext, SERVER_EVENT_ID, request);

        while (responses.get(requestId) == null) {
            try {
                Thread.sleep(10);
            } catch (Exception e) {
                Log.d(TAG, "Exception while waiting: " + e);
            }
        }
        Response response = responses.get(requestId);
        responses.remove(requestId);
        return response;
    }

    public void respond(String requestId, int code, String type, String body) {
        responses.put(requestId, newFixedLengthResponse(Status.lookup(code), type, body));
    }

    private WritableMap toWritableMap(Map<String, Object> map) {
        WritableNativeMap result = new WritableNativeMap();

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                result.putNull(key);
            } else if (value instanceof Map) {
                //noinspection unchecked,rawtypes
                result.putMap(key, toWritableMap((Map) value));
            } else if (value instanceof List) {
                //noinspection unchecked,rawtypes
                result.putArray(key, toWritableArray((List) value));
            } else if (value instanceof Boolean) {
                result.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                result.putInt(key, (Integer) value);
            } else if (value instanceof String) {
                result.putString(key, (String) value);
            } else if (value instanceof Double) {
                result.putDouble(key, (Double) value);
            } else {
                Log.e(TAG, "Could not convert object " + value.toString());
            }
        }

        return result;
    }
    
    private WritableArray toWritableArray(List<Object> array) {
        WritableNativeArray result = new WritableNativeArray();

        for (Object value : array) {
            if (value == null) {
                result.pushNull();
            } else if (value instanceof Map) {
                //noinspection unchecked,rawtypes
                result.pushMap(toWritableMap((Map) value));
            } else if (value instanceof List) {
                //noinspection unchecked,rawtypes
                result.pushArray(toWritableArray((List) value));
            } else if (value instanceof Boolean) {
                result.pushBoolean((Boolean) value);
            } else if (value instanceof Integer) {
                result.pushInt((Integer) value);
            } else if (value instanceof String) {
                result.pushString((String) value);
            } else if (value instanceof Double) {
                result.pushDouble((Double) value);
            } else {
                Log.e(TAG, "Could not convert object " + value.toString());
            }
        }

        return result;
    }
    private WritableMap fillRequestMap(IHTTPSession session, String requestId) throws Exception {
        Method method = session.getMethod();
        WritableMap request = Arguments.createMap();
        request.putString("requestId", requestId);
        request.putString("type", method.name());
        request.putString("url", session.getUri());
        request.putMap("headers", toWritableMap(session.getHeaders()));
        request.putMap("parameters", toWritableMap(session.getParameters()));
        
        Map<String, String> files = new HashMap<>();
        session.parseBody(files);
        if (files.size() > 0) {
          request.putString("postData", files.get("postData"));
        }

        return request;
    }

    private void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, params);
    }
}
