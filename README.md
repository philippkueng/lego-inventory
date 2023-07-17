# a LEGO inventory app

* forked from https://github.com/hyperfiddle/electric-xtdb-starter
* Adapted from [xtdb-in-a-box](https://github.com/xtdb/xtdb-in-a-box)
* Requires env var `XTDB_ENABLE_BYTEUTILS_SHA1=true`

```
$ REBRICKABLE_API_KEY=the-key XTDB_ENABLE_BYTEUTILS_SHA1=true clj -A:dev -X user/main

Starting Electric compiler and server...
shadow-cljs - server version: 2.20.1 running at http://localhost:9630
shadow-cljs - nREPL server started on port 9001
[:app] Configuring build.
[:app] Compiling ...
[:app] Build completed. (224 files, 0 compiled, 0 warnings, 1.93s)

👉 App server available at http://0.0.0.0:8080
```

## database entities

```clojure
{:type :set
 :xt/id (uuid-random)
 :imported-at (System/currentTimeMillis)
 :rebrickable/id "6815-1"
 :rebrickable/name "Hovertron"
 :rebrickable/url "https://rebrickable.com/sets/6815-1/hovertron/#parts"
 :rebrickable/release-year "1996"
 :rebrickable/theme-id 131
 :rebrickable/image-url "https://cdn.rebrickable.com/media/thumbs/sets/6815-1/7046.jpg/1000x800p.jpg?1657462962.3916929"}

{:type :part
 :xt/id (uuid-random)
 :belongs-to internal-set-id ;; :xt/id
 ;; :lego/id "6020" ;; it's just the mold - there's still the color component which the id doesn't include
 :rebrickable/element-id "524523"
 ;;:rebrickable/id "6020"
 :rebrickable/url "https://rebrickable.com/parts/6020/bar-7-x-3-with-double-clips-ladder/" ;; just entering the URL without the name at the end will also find it
 :rebrickable/name "LEGO PART 6020 Bar 7 x 3 with Double Clips (Ladder)"
 :rebrickable/image-url "https://cdn.rebrickable.com/media/thumbs/parts/photos/0/6020-0-e40f4f75-53d5-4d40-aecd-5580488fcd6b.jpg/250x250p.jpg?1658343735.7284539"
 :part/number 32201
 ;;:brickowl/id "245401"
 ;;:brickowl/url "https://www.brickowl.com/catalog/lego-white-brick-1-x-2-x-5-with-stud-holder-2454"
 :color/id 0
 :color/name "Black"}

 ;; if linking between an :owned-set and an :owned-part we can track additional attributes
 :status :added-to-set :missing
 :note "broken, bent, etc."

{:type :owned-set
 :xt/id (uuid-random)
 :of-set internal-set-id ;; :xt/id
 :pictures [:picture]}

{:type :owned-part
 :xt/id (uuid-random)}

{:type :picture
 :xt/id (uuid-random)
 :file-name ""}
```

## tasks which need to be accomplished

- print pictures for the bags based on the information in the database

- let the person enter the lego id on the instructions -> make a search API request -> person selects which set it is
  - fetch all the parts via the API and paginate through to get all the parts of the set
  - insert individual entities for each of the parts and in case the quantity is greater than 1, create multiple `:set-contains-part` entities

- look up parts by their lego id and then choose the color
  - then display the sets this part belongs to and give us an option to assign it to one.

- how can I have different views and track what kind of view is being displayed? watch an atom like it https://electric.hyperfiddle.net/user.tutorial-7guis-5-crud!CRUD and does that work for a multiplayer setup?
