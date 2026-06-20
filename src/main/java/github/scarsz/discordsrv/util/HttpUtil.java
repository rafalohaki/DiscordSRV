/*
 * DiscordSRV - https://github.com/DiscordSRV/DiscordSRV
 *
 * Copyright (C) 2016 - 2024 Austin "Scarsz" Shapiro
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 */

package github.scarsz.discordsrv.util;

import com.github.kevinsawicki.http.HttpRequest;
import github.scarsz.discordsrv.DiscordSRV;

import java.io.File;
import java.util.concurrent.TimeUnit;

public abstract class HttpUtil {

    /**
     * Maximum number of attempts for transient HTTP failures (5xx / connect / read timeouts).
     * Upstream issue #1700: a single 502 Bad Gateway from the update service would silently
     * fail the update check. We now retry with exponential backoff.
     */
    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_BASE_MS = 500L;

    private static HttpRequest setTimeout(HttpRequest httpRequest) {
        return httpRequest
                .connectTimeout(Math.toIntExact(TimeUnit.SECONDS.toMillis(30)))
                .readTimeout(Math.toIntExact(TimeUnit.SECONDS.toMillis(30)));
    }

    /**
     * @return true if the HTTP status code indicates a transient failure worth retrying
     *         (5xx). 4xx and 2xx are not retried.
     */
    private static boolean isTransientStatus(HttpRequest request) {
        int code = request.code();
        return code / 100 == 5;
    }

    public static String requestHttp(String requestUrl) {
        HttpRequest.HttpRequestException lastException = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpRequest request = setTimeout(HttpRequest.get(requestUrl));
                String body = request.body();
                if (isTransientStatus(request) && attempt < MAX_ATTEMPTS) {
                    DiscordSRV.debug("HTTP GET " + requestUrl + " returned " + request.code() + " (attempt " + attempt + "/" + MAX_ATTEMPTS + "), retrying");
                    sleepBackoff(attempt);
                    continue;
                }
                return body;
            } catch (HttpRequest.HttpRequestException e) {
                lastException = e;
                if (attempt < MAX_ATTEMPTS) {
                    DiscordSRV.debug("HTTP GET " + requestUrl + " failed (attempt " + attempt + "/" + MAX_ATTEMPTS + "): " + e.getMessage() + ", retrying");
                    sleepBackoff(attempt);
                }
            }
        }
        DiscordSRV.error(LangUtil.InternalMessage.HTTP_FAILED_TO_FETCH_URL + " " + requestUrl + ": " + (lastException != null ? lastException.getMessage() : "transient HTTP failure"));
        return "";
    }

    public static void downloadFile(String requestUrl, File destination) {
        HttpRequest.HttpRequestException lastException = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpRequest request = setTimeout(HttpRequest.get(requestUrl));
                request.receive(destination);
                if (isTransientStatus(request) && attempt < MAX_ATTEMPTS) {
                    DiscordSRV.debug("HTTP download " + requestUrl + " returned " + request.code() + " (attempt " + attempt + "/" + MAX_ATTEMPTS + "), retrying");
                    sleepBackoff(attempt);
                    continue;
                }
                return;
            } catch (HttpRequest.HttpRequestException e) {
                lastException = e;
                if (attempt < MAX_ATTEMPTS) {
                    DiscordSRV.debug("HTTP download " + requestUrl + " failed (attempt " + attempt + "/" + MAX_ATTEMPTS + "): " + e.getMessage() + ", retrying");
                    sleepBackoff(attempt);
                }
            }
        }
        DiscordSRV.error(LangUtil.InternalMessage.HTTP_FAILED_TO_DOWNLOAD_URL + " " + requestUrl + ": " + (lastException != null ? lastException.getMessage() : "transient HTTP failure"));
    }

    private static void sleepBackoff(int attempt) {
        try {
            Thread.sleep(BACKOFF_BASE_MS * (1L << (attempt - 1))); // 500ms, 1000ms, 2000ms...
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public static boolean exists(String url) {
        try {
            HttpRequest request = setTimeout(HttpRequest.head(url));
            return request.code() / 100 == 2;
        } catch (Exception e) {
            return false;
        }
    }

}
