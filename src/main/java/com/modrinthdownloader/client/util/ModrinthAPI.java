package com.modrinthdownloader.client.util;

import com.google.gson.*;
import com.modrinthdownloader.ModrinthDownloaderClient;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class ModrinthAPI {

    private static final String BASE_URL = "https://api.modrinth.com/v2";
    private static final String USER_AGENT = "MyMinecraftApp/1.0.0 (contact@example.com)";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final Gson GSON = new GsonBuilder().create();

    // ---- Enums ----

    public enum ProjectType {
        MOD("mod", "Mods"),
        MODPACK("modpack", "Modpacks"),
        RESOURCEPACK("resourcepack", "Resource Packs"),
        SHADER("shader", "Shaders"),
        PLUGIN("plugin", "Plugins"),
        DATAPACK("datapack", "Datapacks");

        public final String apiValue;
        public final String displayName;

        ProjectType(String apiValue, String displayName) {
            this.apiValue = apiValue;
            this.displayName = displayName;
        }
    }

    public enum SortOrder {
        RELEVANCE("relevance", "Relevance"),
        DOWNLOADS("downloads", "Downloads"),
        FOLLOWS("follows", "Follows"),
        NEWEST("newest", "Newest"),
        UPDATED("updated", "Recently Updated");

        public final String apiValue;
        public final String displayName;

        SortOrder(String apiValue, String displayName) {
            this.apiValue = apiValue;
            this.displayName = displayName;
        }
    }

    // ---- Data classes ----

    public static class SearchResult {
        public String slug;
        public String title;
        public String description;
        public String projectType;
        public long downloads;
        public long follows;
        public String projectId;
        public String iconUrl;
        public List<String> categories;
        public List<String> versions;
        public String author;

        public SearchResult(JsonObject obj) {
            this.slug        = str(obj, "slug");
            this.title       = str(obj, "title");
            this.description = str(obj, "description");
            this.projectType = str(obj, "project_type");
            this.downloads   = lng(obj, "downloads");
            this.follows     = lng(obj, "follows");
            this.projectId   = str(obj, "project_id");
            this.iconUrl     = str(obj, "icon_url");
            this.author      = str(obj, "author");
            this.categories  = strList(obj, "categories");
            this.versions    = strList(obj, "versions");
        }
    }

    public static class ProjectVersion {
        public String id;
        public String name;
        public String versionNumber;
        public String versionType;
        public long downloads;
        public List<String> gameVersions;
        public List<String> loaders;
        public List<FileInfo> files;

        public ProjectVersion(JsonObject obj) {
            this.id            = str(obj, "id");
            this.name          = str(obj, "name");
            this.versionNumber = str(obj, "version_number");
            this.versionType   = str(obj, "version_type");
            this.downloads     = lng(obj, "downloads");
            this.gameVersions  = strList(obj, "game_versions");
            this.loaders       = strList(obj, "loaders");
            this.files         = new ArrayList<>();
            if (obj.has("files") && obj.get("files").isJsonArray()) {
                for (JsonElement el : obj.getAsJsonArray("files")) {
                    this.files.add(new FileInfo(el.getAsJsonObject()));
                }
            }
        }
    }

    public static class FileInfo {
        public String url;
        public String filename;
        public long size;
        public boolean primary;

        public FileInfo(JsonObject obj) {
            this.url      = str(obj, "url");
            this.filename = str(obj, "filename");
            this.size     = lng(obj, "size");
            this.primary  = obj.has("primary") && !obj.get("primary").isJsonNull()
                             && obj.get("primary").getAsBoolean();
        }
    }

    // ---- API methods ----

    /**
     * Search Modrinth projects with filters.
     */
    public static CompletableFuture<List<SearchResult>> search(
            String query, ProjectType type, SortOrder sort,
            String gameVersion, int limit, int offset) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                StringBuilder url = new StringBuilder(BASE_URL)
                        .append("/search?limit=").append(limit)
                        .append("&offset=").append(offset)
                        .append("&index=").append(sort.apiValue);

                if (query != null && !query.isBlank()) {
                    url.append("&query=").append(URLEncoder.encode(query.trim(), "UTF-8"));
                }

                // Build facets array
                List<String> facetParts = new ArrayList<>();
                facetParts.add("[\"project_type:" + type.apiValue + "\"]");
                if (gameVersion != null && !gameVersion.isEmpty() && !gameVersion.equals("Any Version")) {
                    facetParts.add("[\"versions:" + gameVersion + "\"]");
                }
                url.append("&facets=[").append(String.join(",", facetParts)).append("]");

                String body = get(url.toString());
                JsonObject json = GSON.fromJson(body, JsonObject.class);

                List<SearchResult> results = new ArrayList<>();
                if (json.has("hits") && json.get("hits").isJsonArray()) {
                    for (JsonElement el : json.getAsJsonArray("hits")) {
                        results.add(new SearchResult(el.getAsJsonObject()));
                    }
                }
                return results;
            } catch (Exception e) {
                ModrinthDownloaderClient.LOGGER.error("Modrinth search failed", e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Fetch available versions for a project, filtered by game version and loader.
     */
    public static CompletableFuture<List<ProjectVersion>> getVersions(
            String projectId, String gameVersion, String loader) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                StringBuilder url = new StringBuilder(BASE_URL)
                        .append("/project/").append(projectId).append("/version?");

                if (gameVersion != null && !gameVersion.isEmpty() && !gameVersion.equals("Any Version")) {
                    url.append("game_versions=[\"").append(gameVersion).append("\"]&");
                }
                if (loader != null && !loader.isEmpty()) {
                    url.append("loaders=[\"").append(loader).append("\"]");
                }

                String body = get(url.toString());
                List<ProjectVersion> versions = new ArrayList<>();
                JsonArray arr = GSON.fromJson(body, JsonArray.class);
                for (JsonElement el : arr) {
                    versions.add(new ProjectVersion(el.getAsJsonObject()));
                }
                return versions;
            } catch (Exception e) {
                ModrinthDownloaderClient.LOGGER.error("Get versions failed for " + projectId, e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Download a file to the appropriate game folder.
     * Routes automatically based on project type.
     */
    public static CompletableFuture<Boolean> downloadFile(
            FileInfo file, ProjectType type, Consumer<Double> progressCallback) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                Path gameDir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
                Path destDir = switch (type) {
                    case RESOURCEPACK -> gameDir.resolve("resourcepacks");
                    case SHADER       -> gameDir.resolve("shaderpacks");
                    case DATAPACK     -> gameDir.resolve("saves"); // user moves into world
                    default           -> gameDir.resolve("mods");
                };
                Files.createDirectories(destDir);

                Path destFile = destDir.resolve(file.filename);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(file.url))
                        .header("User-Agent", USER_AGENT)
                        .GET()
                        .build();

                HttpResponse<InputStream> response =
                        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());

                long total = file.size > 0 ? file.size : -1;
                long downloaded = 0;
                byte[] buf = new byte[8192];
                int read;

                try (InputStream in = response.body();
                     OutputStream out = Files.newOutputStream(destFile)) {
                    while ((read = in.read(buf)) != -1) {
                        out.write(buf, 0, read);
                        downloaded += read;
                        if (total > 0 && progressCallback != null) {
                            progressCallback.accept((double) downloaded / total);
                        }
                    }
                }

                ModrinthDownloaderClient.LOGGER.info("Downloaded: " + file.filename + " → " + destDir);
                return true;

            } catch (Exception e) {
                ModrinthDownloaderClient.LOGGER.error("Download failed: " + file.filename, e);
                return false;
            }
        });
    }

    // ---- Internal HTTP ----

    private static String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("HTTP " + resp.statusCode() + " for " + url);
        }
        return resp.body();
    }

    // ---- JSON helpers ----

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static long lng(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsLong() : 0L;
    }

    private static List<String> strList(JsonObject o, String k) {
        List<String> list = new ArrayList<>();
        if (o.has(k) && o.get(k).isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray(k)) list.add(e.getAsString());
        }
        return list;
    }

    // ---- Constants ----

    public static final List<String> COMMON_VERSIONS = List.of(
            "Any Version",
            "26.1", "1.21.1", "1.21", "1.20.4", "1.20.2", "1.20.1", "1.20",
            "1.19.4", "1.19.2", "1.18.2", "1.17.1", "1.16.5", "1.12.2"
    );
}
