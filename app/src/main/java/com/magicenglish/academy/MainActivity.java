package com.magicenglish.academy;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.content.Intent;
import android.speech.tts.TextToSpeech;
import android.provider.MediaStore;
import android.graphics.Bitmap;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private WebView webView;
    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private TextRecognizer textRecognizer;
    private static final int REQ_AUDIO = 11;
    private static final int REQ_CAMERA_IMAGE = 21;
    private static final int REQ_GALLERY_IMAGE = 22;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript("try{Object.defineProperty(Array.prototype,'innerHTML',{set:function(v){var e=document.getElementById('board');if(e)e.innerHTML=v},configurable:true});Array.prototype.appendChild=function(x){var e=document.getElementById('board');if(e)e.appendChild(x)}}catch(e){}", null);
            }
        });
        webView.addJavascriptInterface(new NativeBridge(), "Android");
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        tts = new TextToSpeech(this, status -> { if (status == TextToSpeech.SUCCESS) { tts.setLanguage(Locale.US); tts.setSpeechRate(0.88f); } });
        webView.loadUrl("file:///android_asset/index_v15.html");
    }

    public class NativeBridge {
        @JavascriptInterface public void speak(String text) { runOnUiThread(() -> { if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "magic-english"); }); }
        @JavascriptInterface public void startRecognition() { runOnUiThread(() -> { if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO); return; } beginRecognition(); }); }
        @JavascriptInterface public void takeWordPhoto() { runOnUiThread(() -> { Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE); if (intent.resolveActivity(getPackageManager()) != null) startActivityForResult(intent, REQ_CAMERA_IMAGE); else sendOcrError("没有找到可用的相机应用"); }); }
        @JavascriptInterface public void pickWordPhoto() { runOnUiThread(() -> { Intent intent = new Intent(Intent.ACTION_GET_CONTENT); intent.setType("image/*"); intent.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(Intent.createChooser(intent, "选择课本或单词表图片"), REQ_GALLERY_IMAGE); }); }
    }

    private void beginRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { sendSpeechResult("设备暂不支持语音识别"); return; }
        if (recognizer != null) recognizer.destroy();
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {} @Override public void onBeginningOfSpeech() {} @Override public void onRmsChanged(float rmsdB) {} @Override public void onBufferReceived(byte[] buffer) {} @Override public void onEndOfSpeech() {}
            @Override public void onError(int error) { sendSpeechResult(""); }
            @Override public void onResults(Bundle results) { ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); sendSpeechResult(list != null && !list.isEmpty() ? list.get(0) : ""); }
            @Override public void onPartialResults(Bundle partialResults) {} @Override public void onEvent(int eventType, Bundle params) {}
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "请朗读英文");
        recognizer.startListening(intent);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data); if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQ_CAMERA_IMAGE) { Bundle extras = data.getExtras(); Bitmap bitmap = extras != null ? (Bitmap) extras.get("data") : null; if (bitmap != null) processOcr(InputImage.fromBitmap(bitmap, 0)); else sendOcrError("没有获取到拍照图片"); }
        else if (requestCode == REQ_GALLERY_IMAGE) { Uri uri = data.getData(); if (uri != null) { try { processOcr(InputImage.fromFilePath(this, uri)); } catch (IOException e) { sendOcrError("读取图片失败"); } } }
    }
    private void processOcr(InputImage image) { runOnUiThread(() -> webView.evaluateJavascript("window.onOcrStarted&&window.onOcrStarted()", null)); textRecognizer.process(image).addOnSuccessListener(this::sendOcrResult).addOnFailureListener(e -> sendOcrError("文字识别失败：" + e.getMessage())); }
    private void sendOcrResult(Text visionText) { String safe = jsEscape(visionText != null ? visionText.getText() : ""); runOnUiThread(() -> webView.evaluateJavascript("window.onOcrResult&&window.onOcrResult('" + safe + "')", null)); }
    private void sendOcrError(String message) { String safe = jsEscape(message == null ? "识别失败" : message); runOnUiThread(() -> webView.evaluateJavascript("window.onOcrError&&window.onOcrError('" + safe + "')", null)); }
    private void sendSpeechResult(String result) { String safe = jsEscape(result == null ? "" : result); runOnUiThread(() -> webView.evaluateJavascript("window.onSpeechResult('" + safe + "')", null)); }
    private String jsEscape(String s) { return s.replace("\\", "\\\\").replace("'", "\\'").replace("\r", "\\r").replace("\n", "\\n").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029"); }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) { super.onRequestPermissionsResult(requestCode, permissions, grantResults); if (requestCode == REQ_AUDIO && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) beginRecognition(); }
    @Override protected void onDestroy() { if (tts != null) { tts.stop(); tts.shutdown(); } if (recognizer != null) recognizer.destroy(); if (textRecognizer != null) textRecognizer.close(); if (webView != null) webView.destroy(); super.onDestroy(); }
}
