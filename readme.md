# bsky-api
A very rudimental implementation of ATProto in Java and OkHttpClient.

## Disclaimer
The purpose of this project is to provide an API to fetch posts and perform basic operations. Advanced features like messaging or account creation are not supported.

## Note
Java classes are generated from the official `Lexicons`, using the nested "gen" gradle project. `Lexicons` aren't included in this project.
You must have the lexicons in "gen/run/lexicons". You can get these lexicons from [Bsky's GitHub project](https://github.com/bluesky-social/atproto/tree/main/lexicons). 
To generate and save the classes, run `Lexicon2Java.kt` (you need to run this from the root directory, not inside the gen folder). Classes are generated at `src/main/java`.<p>
You might need to delete some `Output` classes to get the project to compile.  

## Usage
You can perform most operations using `dev.seeight.bsky.BskyOps` and `dev.seeight.bsky.BskyApi`. 

### Example
Favorite/like a post
```java
class Example {
    static void main(String[] args) {
        OkHttpClient client = new OkHttpClient.Builder().build();
        Gson gson = GsonCreator.addTypeAdapter(new GsonBuilder()).create();
        BskyApi api = new BskyApi(pdsUrl, accessJwt, refreshJwt, gson, client);
        BskyOps ops = new BskyOps(api);
        
        GetSession.Main.Output session;
        try {
            session = ops.getSession();
            System.out.println("Logged in as @" + g.handle);
        } catch (BskyError e) {
            if (e.error.equals("ExpiredToken")) {
                System.out.println("Token expired, making new one...");
                var z = ops.refreshSession();
                api.onRefreshSession(z);
                session = ops.getSession();
                System.out.println("Logged in as @" + g.handle);
            } else throw e;
        }

        String postUri = "";
        String postCid = "";
        String repo = session.did;
        String collection = "app.bsky.feed.like";
        ops.createRecord(
            repo, collection, null, true,
            new Like.Main(
                new StrongRef.Main(
                    postUri, postCid
                ),
                ZonedDateTime.now()
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            ), null
        );
    }
}
```

Getting a thread from a post
```java
class Example {
    static void main(String[] args) {
        OkHttpClient client = new OkHttpClient.Builder().build();
        Gson gson = GsonCreator.addTypeAdapter(new GsonBuilder()).create();
        BskyApi api = new BskyApi(pdsUrl, accessJwt, refreshJwt, gson, client);
        BskyOps ops = new BskyOps(api);
        try {
            var g = ops.getSession();
            System.out.println("Logged in as @" + g.handle);
        } catch (BskyError e) {
            if (e.error.equals("ExpiredToken")) {
                System.out.println("Token expired, making new one...");
                var z = ops.refreshSession();
                api.onRefreshSession(z);
                var g = ops.getSession();
                System.out.println("Logged in as @" + g.handle);
            }
        }
        GetPostThread.Main.Output thread = ops.getPostThread("at://unicouniuni3.bsky.social/app.bsky.feed.post/3mqhbk6mhhc2m", 1, 1);
        Files.writeString(Path.of("./output.json"), gson.toJson(thread));
    }
}
```
Additionally, you might need `dev.seeight.bsky.Simplifier` to access media inside posts. It's a mess otherwise.