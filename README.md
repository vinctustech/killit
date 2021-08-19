killit
======

*killit* is a little command to kill any process that is listening on a given port.  Currently, it only checks TCP6 connections.

Installation
------------

Install it by downloading the file `killit` from the repository.  Then make it executable:

```chmod a+x killit```

and copy it to where it can be found in your path:

```sudo cp killit /usr/bin```

Usage
-----

Let's say your server wants to listen on port 3410, but you're getting an annoying message like:

```
Error: listen EADDRINUSE: address already in use :::3410
```

You could do a test run by typing:

```bash
killit -t 3410
```

getting output similar to:

```
TCP6 local address connections on port 3410 (0D52):
  00000000000000000000000000000000:0D52 0A 26379226
processes that are listening on 3410:
  inode 26379226:
    2008300
would kill 1 process(es) with pid(s): 2008300
```

To actually kill that zombie process type:

```bash
killit 3410
```
