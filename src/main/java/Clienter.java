import org.apache.commons.codec.binary.Hex;
import ws.WebsocketClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Clienter {

    public static void main(final String[] args) throws URISyntaxException, SSLException {
        String WS_ENDPOINT="ws://localhost:8080/ws";
        String path="ws";
        String API_KEY="Fq2PcRg1xF1jLuFC7w7kZD7PFR9AbKTkisUETYRH1oOZTf2LyWs+H$v1";
        String API_SECRET="zCXXZ-JhPl4e5ozCgLGqPsmIJTlyaoVIJ1Tt#uTo3y%pur9Bone4$t66";

        final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
        final WebsocketClient websocketClient = new WebsocketClient(WS_ENDPOINT,new WebsocketClient.Handler() {
            @Override
            public void onConnected(WebsocketClient client) {
            }

            @Override
            public void onClose() {
            }

            @Override
            public void onError(Throwable cause) {
            }

            @Override
            public void onTextMessage(byte[] bytes) {
                final String message = new String(bytes, StandardCharsets.UTF_8);
                System.out.println("onMessage : " + message + "");
            }

            @Override
            public void onBinaryMessage(InputStream inputStream) {
            }
        }){
            @Override
            public void beforeStart() {
                final long exp = createExpires();
                final String sign;
                try {
                    sign = encrypt("GET", path, "", exp, API_SECRET);
                    final Map<String, String> headers = createAuthenticationHeaders(API_KEY, sign, exp);
                    headers.forEach((k, v) -> header(k, v));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        websocketClient.start();
        executorService.scheduleAtFixedRate(() -> send(websocketClient), 200,  1000, TimeUnit.MILLISECONDS);
        while (true){

        }
    }

    private static long createExpires() {
        return System.currentTimeMillis() / 1000 + 60;
    }

    private static void send(WebsocketClient websocketClient){
        websocketClient.sendString("{ \"command\":\"subscribe\", \"engineId\":\"test-george\"}");
    }

    private static String encrypt(final String method, final String requestPath, final String body, final long timestamp, final String secret) throws Exception {
        final StringBuilder sign = new StringBuilder();
        sign.append(String.valueOf(timestamp)).append(method).append("/").append(requestPath);
        if (body != null) {
            sign.append(body);
        }
        final byte[] hash = hmacSHA256(sign.toString(), secret.getBytes(StandardCharsets.UTF_8));
        return Hex.encodeHexString(hash);
    }

    private static Map<String, String> createAuthenticationHeaders(final String key,final String sign, final long timestamp) {
        final Map<String, String> map = new HashMap();
        map.put("API-KEY", key);
        map.put("API-SIGN", sign);
        map.put("API-TS", String.valueOf(timestamp));
        return map;
    }

    private static byte[] hmacSHA256(final String message, final byte[] secret) throws Exception {
        final SecretKeySpec secretKey = new SecretKeySpec(secret, "HmacSHA256");
        final Mac sha256HMAC = Mac.getInstance("HmacSHA256");
        sha256HMAC.init(secretKey);
        return sha256HMAC.doFinal(message.getBytes());
    }
}
