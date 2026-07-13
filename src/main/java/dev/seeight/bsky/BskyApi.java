/*
 * bsky-api.main
 * Copyright (C) 2026 c8ff
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.seeight.bsky;

import com.atproto.server.CreateSession;
import com.atproto.server.DeleteSession;
import com.atproto.server.RefreshSession;
import com.google.gson.*;
import dev.seeight.util.MiscUtil;
import okhttp3.*;
import okio.BufferedSink;
import okio.ByteString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Objects;

public class BskyApi {
	public static @Nullable String useragent = "Mozilla/5.0 (X11; Linux x86_64; rv:149.0) Gecko/20100101 Firefox/149.0";
    private String pdsUrl;
    private String accessJwt;
    private String refreshJwt;
    private final Gson gson;
    private final OkHttpClient client;

    public BskyApi(String pdsUrl, String accessJwt, String refreshJwt, Gson gson, OkHttpClient client) {
        if (pdsUrl.endsWith("/")) {
            this.pdsUrl = pdsUrl.substring(0, pdsUrl.length() - 1);
        } else this.pdsUrl = pdsUrl;
        this.accessJwt = accessJwt;
        this.refreshJwt = refreshJwt;
        this.gson = gson;
        this.client = client;
    }

	public String getRefreshJwt() {
		return refreshJwt;
	}

	public String getAccessJwt() {
		return accessJwt;
	}

	public String getPdsUrl() {
		return pdsUrl;
	}

	public void onRefreshSession(RefreshSession.Main.Output a) {
        this.accessJwt = a.accessJwt;
        this.refreshJwt = a.refreshJwt;
        // a lexicon for this property is not available. we're forced to parse this manually.
        this.pdsUrl = a.didDoc.getAsJsonObject().getAsJsonArray("service")
                .get(0).getAsJsonObject().get("serviceEndpoint").getAsString();
    }

	public void onRefreshSession(CreateSession.Main.Output a) {
        this.accessJwt = a.accessJwt;
        this.refreshJwt = a.refreshJwt;
        this.pdsUrl = a.didDoc.getAsJsonObject().getAsJsonArray("service")
                .get(0).getAsJsonObject().get("serviceEndpoint").getAsString();
    }

    private void addHeaders(Request.Builder req, boolean useRefreshJwt) {
        req.addHeader("authorization", "Bearer " + (useRefreshJwt ? this.refreshJwt : this.accessJwt));
		if (useragent != null) req.addHeader("User-Agent", useragent);
        req.addHeader("TE", "trailers");
        req.addHeader("Sec-Fetch-Site", "cross-site");
        req.addHeader("Sec-Fetch-Mode", "cors");
        req.addHeader("Sec-Fetch-Dest", "empty");
        req.addHeader("Referer", "https://bsky.app/");
        req.addHeader("Priority", "u=4");
        req.addHeader("Origin", "https://bsky.app");
        req.addHeader("Pragma", "no-cache");
    }

    public <T extends BskyObject> T xrpcPost(Class<? extends BskyOperation> operationClass, Class<T> outputType, @Nullable BskyObject input) {
        if (outputType == null) throw new IllegalArgumentException("Output type cannot be null.");

        var path = operationClass.getAnnotation(OperationPath.class);
        if (path == null) throw new IllegalStateException("XRPC operation doesn't have an 'OperationPath' annotation.");

        var encoding = operationClass.getAnnotation(QEncoding.class);
        if (encoding == null) throw new IllegalStateException("XRPC operation doesn't have an 'QEncoding' annotation.");

        if (!encoding.output().equals("application/json") && !encoding.output().equals("none")) {
            throw new UnsupportedOperationException("Not implemented.");
        }

		var pds = this.pdsUrl;
		if (operationClass == CreateSession.Main.class || (operationClass == DeleteSession.Main.class && pds == null)) {
			pds = "https://bsky.social";
		}
		if (pds == null) throw new IllegalStateException("lol");

        var url = HttpUrl.get(pds + "/xrpc/" + path.value()).newBuilder();
        var req = new Request.Builder().url(url.build());

        if (input != null && encoding.input().equals("application/json")) {
            // TODO: type?
            String j = gson.toJson(input);
            req.post(RequestBody.create(j, MediaType.parse("application/json")));
        } else if (encoding.input().equals("params")) {
            req.post(new RequestBody() {
				@Override
				public @Nullable MediaType contentType() {
					return null;
				}

				@Override
				public long contentLength() {
					return ByteString.EMPTY.size();
				}

				@Override
				public void writeTo(@NotNull BufferedSink sink) throws IOException {
					sink.write(ByteString.EMPTY);
				}
			});
        } else
            throw new IllegalStateException();

        addHeaders(req, operationClass == RefreshSession.Main.class || operationClass == DeleteSession.Main.class);
        try (var response = client.newCall(req.build()).execute()) {
            if (encoding.output().equals("none")) {
                if (response.code() != HttpURLConnection.HTTP_OK) {
                    throw new RuntimeException("Response code was not OK: " + response.code());
                }

                return null;
            }

            String string = response.body().string();
            JsonElement e = JsonParser.parseString(string);
            if (response.code() != HttpURLConnection.HTTP_OK) {
                if (e instanceof JsonObject z && z.has("error")) {
                    throw new BskyError(z.get("error").getAsString(), z.get("message").getAsString());
                }
                throw new RuntimeException("Response code was not OK: " + response.code());
            }
            return gson.fromJson(e, outputType);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public <T extends BskyObject> T xrpcGet(Class<? extends BskyOperation> operationType, Class<T> outputType, @Nullable BskyOperation operation) {
        if (outputType == null) throw new IllegalArgumentException("Output type cannot be null.");

        var path = operationType.getAnnotation(OperationPath.class);
        if (path == null) throw new IllegalStateException("XRPC operation doesn't have an 'OperationPath' annotation.");

        var encoding = operationType.getAnnotation(QEncoding.class);
        if (encoding == null) throw new IllegalStateException("XRPC operation doesn't have an 'QEncoding' annotation.");

        if (!encoding.output().equals("application/json") && !encoding.output().equals("none")) {
            throw new UnsupportedOperationException("Not implemented.");
        }

        var url = HttpUrl.get(this.pdsUrl + "/xrpc/" + path.value()).newBuilder();
        var req = new Request.Builder().get();
        if (operationType.isAnnotationPresent(Query.class) && encoding.input().equals("params")) {
            if (operation != null) {
				buildQueryParams: {
					// bsky requires multiple query parameters with the same name instead of an array for some requests.
					// no idea who was the genius behind this
					if (operationType.getFields().length == 1) {
						var f = operationType.getFields()[0];
						var type = f.getGenericType();
						if (type instanceof ParameterizedType type1) {
							var a = type1.getActualTypeArguments();
							if (List.class.isAssignableFrom(f.getType()) && a.length > 0 && Objects.equals(a[0].getTypeName(), "java.lang.String")) {
								try {
									var r = f.get(operation);
									if (r == null)
										throw new IllegalStateException("Field '" + f.getName() + "' is apparently required.");
									//noinspection unchecked
									var e = ((List<String>) r);
									if (e.isEmpty())
										throw new IllegalStateException("Field '" + f.getName() + "' must not be empty.");
									for (String uris : e) {
										url.addQueryParameter(f.getName(), uris);
									}
								} catch (IllegalAccessException e) {
									MiscUtil.sneakyThrow(e);
								}
								break buildQueryParams;
							}
						}
					}

					// Generic handling
					for (Field f : operationType.getFields()) {
						if (Modifier.isStatic(f.getModifiers())) continue;
						if (Modifier.isTransient(f.getModifiers())) continue;
						try {
							Object v;
							v = f.get(operation);
							if (v == null) {
								if (f.isAnnotationPresent(Required.class))
									throw new IllegalStateException("Field " + f.getName() + " is null, but it's required.");
								continue;
							}
							url.addQueryParameter(f.getName(), v.toString());
						} catch (IllegalAccessException e) {
							System.err.println("Unable to access query field: " + operationType.getName() + "$" + f.getName());
						}
					}
				}
			}
        } else throw new AssertionError("Invalid operation.");

        req.url(url.build());
        addHeaders(req, false);
        try (var response = client.newCall(req.build()).execute()) {
            if (encoding.output().equals("none")) {
                if (response.code() != HttpURLConnection.HTTP_OK) {
                    throw new RuntimeException("Response code was not OK: " + response.code());
                }
                return null;
            }

            String string = response.body().string();
            try {
                JsonElement e = JsonParser.parseString(string);
                if (response.code() != HttpURLConnection.HTTP_OK) {
                    if (e instanceof JsonObject z && z.has("error")) {
                        throw new BskyError(z.get("error").getAsString(), z.get("message").getAsString());
                    }
                    throw new RuntimeException("Response code was not OK: " + response.code());
                }
                return gson.fromJson(e, outputType);
            } catch (JsonParseException e) {
                throw new RuntimeException("source: " + string, e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
