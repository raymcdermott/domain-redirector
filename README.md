# domain-redirector

A Clojure application designed to manage domain redirection using data from a JSON document.

The JSON document can be loaded from disk or MongoDB.

### Response: HTTP 301

The library maps the original domain, transforms as appropriate and returns the new URL via HTTP 301.

The meaning of HTTP 301 is Moved Permanently. The semantics of this must fit your needs if you wish to use this library.

The detailed semantics of HTTP 301 is explained in [more detail on Wikipedia](https://en.wikipedia.org/wiki/HTTP_301)

Specifically, 301 is recommended by Google to change the URL of a page as it is shown in search engine results.

Usually a browser will follow redirects automatically and many proxies will do likewise by default or through simple configuration.

## What redirections are supported?

```
foo.com -> bar.com
```

```
foo.com/google -> www.google.com
```

```
http://foo.com/secure -> https://my-secure-site.com
```

See the examples section to see how to make this work

## Networking pre-requisites

Assuming that foo.com is not this domain, the DNS CNAME for foo.com must point to the domain of this app. Depending on where 
and how you host the app, using DNS A records is possible but more fragile (because you need to use a fixed IP address).

## Usage

A JSON document is used to define the transformations that are required. The document requires two objects: source and target.

The table lists the supported properties for *source*:

| Property      | Type     | Values          | Required?  | Default Value |
| --------      | -------- | --------------- | --------   | ------------- |
| domain        | []string | domain name(s)  | *Yes*      | None          |
| path          | string   | resource path   | No         | None          |

The table lists the supported properties for *target*:

| Property      | Type    | Values          | Required?  | Default Value   |
| --------      | ------- | --------------- | --------   | --------------- |
| scheme        | string  | http or https   | No         | Incoming scheme |
| domain        | string  | domain name     | *Yes*      | None            |
| path          | string  | resource path   | *Yes*      | None            |

## Performance

The data for redirects does not usually change frequently which means that caching is an effective way to improve performance.

The calls from Mongo are cached in REDIS. The calls from REDIS are *memoized* by default for 1 minute.

This means that the system will generally be quite quick for the first call and very quick on subsequent calls even if calls are spaced out.

It will become quicker under moderate pressure for the same URLs since most of the calls will then operate within the memory of the application.

On a recent laptop (2013 Macbook Pro) I see Mongo in the order of ~10ms, REDIS ~1ms and the in memory or memoised responses ~1 microsecond.

By default the performance is decent and will scale horizontally. There are also plenty of tweaks available on each layer without big changes.

## Limitations

In the existing design, no attempt is made to validate the transformed URLs for dead links.

## Choice of local or network storage

If PREFER_NETWORK_BACKING_STORE is set to true the service will check for MongoDB (see Dependencies). If not the service expects the data to be loadable from the file system. By default it will choose a file called 'domains.json' in the project folder.

## Notes for PAAS platforms

### Heroku

The source domain must be added to the list of domains supported by the application. 

This feature is *NOT* provided by this application.

## Dependencies

If you wish to use an external backing store you will need to install locally or have services that provide MongoDB and REDIS.

**Remember: You must set the environment variable PREFER_NETWORK_BACKING_STORE to true**

The application will attach to these services using the following variables / defaults
 
| Name          | Default Value |
| --------      | ------------- |
| MONGO_URL     | mongodb://localhost/test |
| REDIS_URL     | 127.0.0.1:6379 |

## Options

You can further tweak the application behaviour with a small number of options

| Name              | Meaning          | Default Value |
| ----------------- | -------          | ------------- |
| PORT              | Port # for exposed HTTP endpoint | 5000 |
| MONGO_COLLECTION  | Document collection name | redirections |
| MEMOIZE_TTL_SECONDS | # Seconds Clojure caches values | 60 |
| REDIS_TTL_SECONDS | # Seconds REDIS caches values | 1800 |

## Examples

###### Example 1: Domain redirection

```JavaScript
{
    "source" : {
        "domain" : [ "localhost" ]
    },
    "target" : {
        "domain" : "www.google.com"
    }
}
```
```
$ curl -I http://localhost:5000/google
HTTP/1.1 301 Moved Permanently
Date: Mon, 16 Mar 2015 15:56:17 GMT
Location: http://www.google.com/
Content-Length: 0
Server: Jetty(7.6.13.v20130916)
```

###### Example 2: Domain redirection from domain/path to new domain with https

```JavaScript
{
    "source" : {
        "domain" : [ "localhost" ],
        "path" : "/google"
    },
    "target" : {
        "scheme" : "https",
        "domain" : "www.google.com"
    }
}
```

```
$ curl -I http://localhost:5000/google
HTTP/1.1 301 Moved Permanently
Date: Mon, 16 Mar 2015 15:56:17 GMT
Location: https://www.google.com/
Content-Length: 0
Server: Jetty(7.6.13.v20130916)
```

###### Example 3: Domain + path redirection

```JavaScript
{
    "source" : {
        "domain" : [ "localhost" ],
        "path" : "/prius"
    },
    "target" : {
        "domain" : "www.toyota-europe.com",
        "path" : "/new-cars/prius"
    }
}
```

```
$ curl -I http://localhost:5000/prius
HTTP/1.1 301 Moved Permanently
Date: Mon, 16 Mar 2015 15:55:13 GMT
Location: http://www.toyota-europe.com/new-cars/prius
Content-Length: 0
Server: Jetty(7.6.13.v20130916)
```

###### Example 3: Domain + path redirection with multiple source domains

```JavaScript
{
    "source" : {
        "domain" : [ "localhost", "my-host", "other-host" ],
        "path" : "/new-site"
    },
    "target" : {
        "domain" : "www.my-new-site.com",
        "path" : "/new-path"
    }
}
```

```
$ curl -I http://localhost:5000/new-site
HTTP/1.1 301 Moved Permanently
Date: Mon, 16 Mar 2015 15:55:13 GMT
Location: http://www.my-new-site.com/new-path
Content-Length: 0
Server: Jetty(7.6.13.v20130916)
```

###### Example 4: Forwarding attributes as headers

```JavaScript
{
      "source": {
            "domain": [ "localhost" ],
            "path": "/prius",
            "attributeMap": [
                {
                    "query-string": "token",
                    "header": "Location",
                    "headerProperty": "Bearer"
                }
            ]
      },
      "target": {
            "domain": "www.toyota-europe.com",
            "path": "/new-cars/prius"
      }
}
```

```
$ curl -I http://localhost:5000/prius?token=abc-123
HTTP/1.1 301 Moved Permanently
Date: Mon, 16 Mar 2015 15:55:13 GMT
Authorization: Bearer abc-123
Location: http://www.toyota-europe.com/new-cars/prius
Content-Length: 0
Server: Jetty(7.6.13.v20130916)
```

## Testing

The application comes with a number of pre-built tests to ensure that the general logic is valid.

To confirm, you can run inspect the tests and run them with 'lein test'

Please extend the test suite with your specific needs.

## Pull requests

I will accept pull requests for the core logic and especially for any new tests.

## FAQ

### Do you support query parameter forwarding

Yes. Any query parameters are passed along unaltered. Specifically, no attempt is made to decode or encode query parameters.

### Do you non-exact path matching (for example with regexes?)

No, it only supports the simplest path matching where the path URL starts with the path given in the JSON file.

### Do you support path transformations (for example with regexes?)

Not yet. I would like to do this using core.logic rather than regex

### Is it possible to use this application as a reverse proxy?

No. That's another use case.

### Is it possible to use this application as a URL shortener?

No. That's also another use case.

### TODO List

- support forwarding attributes as headers (for example authentication tokens)
- Support checking of forwarded links (core.async)
- Support powerful regular expression style logic for route matching (core.match / core.logic)


## License

Copyright © 2015 OpenGrail BVBA

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.