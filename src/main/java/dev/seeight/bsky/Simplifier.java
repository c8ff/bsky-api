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

import app.bsky.embed.*;
import app.bsky.embed.Record;
import app.bsky.feed.Defs;
import dev.seeight.gen.Unions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Simplifier {
	public static @Nullable Unions.Union11 getQuotePost(
		Defs.PostView post
	) {
		if (post.embed == null) return null;

		if (post.embed instanceof Record.View z) {
			return z.record;
		} else if (post.embed instanceof RecordWithMedia.View z) {
			return z.record.record;
		}

		if (post.embed instanceof Images.View
			|| post.embed instanceof Video.View
			|| post.embed instanceof Gallery.View
			|| post.embed instanceof External.View
		) {
			return null;
		}
		throw new UnsupportedOperationException("Unsupported embed type: " + post.embed.getClass().getName());
	}

	public static @Nullable Unions.Union11 getQuotePost(
		Record.ViewRecord post
	) {
		if (post.embeds == null) return null;
		for (Unions.Union12 embed : post.embeds) {
			// what
			if (embed instanceof Record.View j) {
				return j.record;
			} else if (embed instanceof RecordWithMedia.View v) {
				return v.record.record;
			}

			if (embed instanceof Images.View || embed instanceof Video.View || embed instanceof Gallery.View || embed instanceof External.View) {
				continue;
			}
			throw new UnsupportedOperationException("Unsupported embed type: " + embed.getClass().getName());
		}
		return null;
	}

	public static Defs.PostView viewRecordToPostView(
		Record.ViewRecord record
	) {
		if (record.embeds != null && record.embeds.size() > 1) {
			throw new IllegalStateException();
		}

		return new Defs.PostView(
			record.uri,
			record.cid,
			record.author,
			record.value,
			record.embeds != null && !record.embeds.isEmpty() ? record.embeds.get(0) : null,
			-1,
			record.replyCount,
			record.repostCount,
			record.likeCount,
			record.quoteCount,
			record.indexedAt,
			null, record.labels, null, null
		);
	}

	public static class SimplifiedMedia {
		public String alt;
		public String url;
		public String thumbnailUrl;
		public int width;
		public int height;
		public MediaType type;

		public enum MediaType {
			IMAGE,
			VIDEO
		}

		public SimplifiedMedia(String alt, String url, String thumbnailUrl, int width, int height, MediaType type) {
			this.alt = alt;
			this.url = url;
			this.thumbnailUrl = thumbnailUrl;
			this.width = width;
			this.height = height;
			this.type = type;
		}

		public SimplifiedMedia() {
		}

		public static @NotNull List<SimplifiedMedia> extractFromPost(
			@NotNull Defs.PostView post
		) {
			return extractFromObject(post.embed, post.author.did);
		}

		public static @NotNull List<SimplifiedMedia> extractFromObject(
			@Nullable BskyObject gurt,
			@Nullable String authorDid
		) {
			if (gurt == null) return Collections.emptyList();

			if (gurt instanceof RecordWithMedia.View view) {
				return extractFromObject(view.media, authorDid);
			} else if (gurt instanceof RecordWithMedia.Main view) {
				return extractFromObject(view.media, authorDid);
			}


			// bsky has multiple structures for describing effectively the same thing.
			// I was a *bit* frustrated when figuring this out.
			// just know that I deleted my frustrated comments for your reading pleasure.

			if (gurt instanceof Images.View view) {
				var o = new ArrayList<SimplifiedMedia>();
				for (Images.ViewImage image : view.images) {
					o.add(new SimplifiedMedia(image.alt,
						image.fullsize,
						image.thumb,
						image.aspectRatio != null ? image.aspectRatio.width : -1,
						image.aspectRatio != null ? image.aspectRatio.height : -1, MediaType.IMAGE));
				}
				return o;
			} else if (gurt instanceof Video.View view) {
				return List.of(new SimplifiedMedia(
					view.alt,
					view.playlist,
					view.thumbnail,
					view.aspectRatio != null ? view.aspectRatio.width : -1,
					view.aspectRatio != null ? view.aspectRatio.height : -1
					, MediaType.VIDEO
				));
			} else if (gurt instanceof Gallery.View view) {
				var o = new ArrayList<SimplifiedMedia>();
				for (Unions.Union10 item : view.items) {
					if (!(item instanceof Gallery.ViewImage image)) throw new AssertionError();
					o.add(new SimplifiedMedia(
						image.alt,
						image.fullsize,
						image.thumbnail,
						image.aspectRatio.width,
						image.aspectRatio.height
						, MediaType.IMAGE
					));
				}
				return o;
			}

			if (gurt instanceof Images.Main v) {
				var r = new ArrayList<SimplifiedMedia>();
				for (Images.Image image : v.images) {
					r.add(new SimplifiedMedia(
						image.alt,
						urlFromStupidLink(image.image.ref.$link, authorDid, false),
						urlFromStupidLink(image.image.ref.$link, authorDid, true),
						image.aspectRatio != null ? image.aspectRatio.width : -1,
						image.aspectRatio != null ? image.aspectRatio.height : -1,
						MediaType.IMAGE
					));
				}
				return r;
			}
			if (gurt instanceof Gallery.Main v) {
				var r = new ArrayList<SimplifiedMedia>();
				for (var j : v.items) {
					if (!(j instanceof Gallery.Image image))
						throw new AssertionError("Gallery has unknown type: " + j.getClass().getName());
					r.add(new SimplifiedMedia(
						image.alt,
						urlFromStupidLink(image.image.ref.$link, authorDid, false),
						urlFromStupidLink(image.image.ref.$link, authorDid, true),
						image.aspectRatio.width,
						image.aspectRatio.height,
						MediaType.IMAGE
					));
				}
				return r;
			}
			if (gurt instanceof Video.Main v) {
				return List.of(new SimplifiedMedia(
					v.alt,
					videoUrlFromStupidLink(v.video.ref.$link, authorDid, false),
					videoUrlFromStupidLink(v.video.ref.$link, authorDid, true),
					v.aspectRatio != null ? v.aspectRatio.width : -1,
					v.aspectRatio != null ? v.aspectRatio.height : -1,
					MediaType.VIDEO
				));
			}

			// TODO: Add support for links lol
			if (gurt instanceof External.View || gurt instanceof External.Main) {
				return Collections.emptyList();
			}

			if (gurt instanceof Record.View || gurt instanceof Record.Main) {
				return Collections.emptyList();
			}
			throw new UnsupportedOperationException("Unsupported object type: " + gurt.getClass().getName());
		}
	}

	public static String urlFromStupidLink(String link, String authorDid, boolean thumbnail) {
		if (authorDid == null) {
			throw new IllegalStateException("No author did provided, but it's required to extract correct URL.");
		}

		if (thumbnail) return "https://cdn.bsky.app/img/feed_thumbnail/plain/" + authorDid + "/" + link;
		return "https://cdn.bsky.app/img/feed_fullsize/plain/" + authorDid + "/" + link;
	}

	public static String videoUrlFromStupidLink(String link, String authorDid, boolean thumbnail) {
		if (authorDid == null) {
			throw new IllegalStateException("No author did provided, but it's required to extract correct URL.");
		}

		if (thumbnail) return "https://video.bsky.app/watch/" + authorDid + "/" + link + "/thumbnail.jpg";
		return "https://video.bsky.app/watch/" + authorDid + "/" + link + "/playlist.m3u8";
	}
}
