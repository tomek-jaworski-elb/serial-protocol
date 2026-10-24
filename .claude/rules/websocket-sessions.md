---
paths:
  - "src/main/java/**/configuration/*WebSocket*.java"
  - "src/main/java/**/configuration/*Handler*.java"
  - "src/main/java/**/configuration/ThreadPoolExecutorConfig.java"
  - "src/main/java/**/service/WSSessionManager.java"
  - "src/main/java/**/service/WebSocketPublisher.java"
  - "src/main/java/**/service/impl/WebSocketPublisher*.java"
  - "src/main/java/**/serial/SessionType.java"
  - "src/main/resources/static/js/ws-*.js"
  - "src/main/resources/templates/fragment.html"
  - "src/test/java/**/configuration/**"
---

# WebSocket channels and the four meanings of "session"

Loaded when you touch the WebSocket configuration, handlers, publisher, `SessionType`, the `ws-*.js` scripts or the shared footer. Invariants: count pages with `openPageCount()`, liveness comes only from page traffic, `SessionType` is a Java-to-JS contract (`CLAUDE.md`).

## WebSocket channels
| Channel | Content |
|---|---|
| `/rs` | raw serial frame as byte-array string |
| `/json` | `ModelTrackDTO` as JSON |
| `/heartbeat` | heartbeat tick (`ws.heartbeat.interval`) |
| `/session` | open-page count (one connection per loaded page, from the footer) |

`SessionType` enum is the shared contract between Java and JS. `WebSocketConfiguration` wires one handler per channel (`RSWebsocketHandler`, `JsonWebSocketHandler`, `HeartBeatWebSocketHandler`, `SessionCountWebSockerHandler` — the typo in that class name is real). `WSSessionManager` stores connections globally; `WebSocketPublisherImpl` fans out via `ThreadPoolExecutorConfig` thread pool.

Ask for the open-page count with `WebSocketPublisher.openPageCount()`, never by counting the whole registry: a page holds one to three connections depending on what it displays, so counting all of them counted subscriptions and showed two tabs as three.

`SessionCountWebSockerHandler` broadcasts the count to every page when a connection opens or closes. A `@Scheduled` sweep, `closeSilentPages()`, runs every `ws.session.silence-check` (30000 ms) and closes `/session` connections that have sent nothing for longer than `ws.session.silence-limit` (150s — generous because a background tab's timers are throttled to roughly once a minute). The sweep does not broadcast itself; closing a connection triggers `afterConnectionClosed`, which removes it and then broadcasts.

## "Session" means four different things here
Be precise about which one you mean, because conflating them is what broke the footer:

| Term | What it is |
|---|---|
| **channel** | a named stream (`/rs`, `/json`, `/heartbeat`, `/session`) — `SessionType` calls these "session types", which is a misnomer |
| **connection** | one page subscribed to one channel; `WSSessionManager` holds all of them |
| **login session** | a signed-in user's server-side session, `server.servlet.session.timeout` |
| **open page** | one loaded page, from load until its tab closes |

The footer's **"Active sessions"** is a count of **open pages** — the label predates this note and is kept deliberately. It works because the footer is on every page and opens exactly one `/session` connection, so counting that channel counts pages. Counting connections instead counted subscriptions, and a page holds one to three of them depending on what it displays: two tabs on the chart used to report three.

A page also sends a keep-alive on that connection every 30s (`js/ws-session-count.js`). It has to come from the page, and the reason is that there are two different clocks:

- The **container idle timeout** (`WebSocketConfiguration`, `setMaxSessionIdleTimeout`, 3 minutes) is reset by traffic in *either* direction. The server writes to every open page whenever another page opens or closes (`broadcastOpenPageCount()`, which is required), and that write resets this clock for pages that are already gone too.
- **`lastHeardFrom`** is written only when the *page* sends something. `closeSilentPages()` compares it with `ws.session.silence-limit`. Server writes cannot touch it.

A window killed by a crash never sends a close frame, so silence on the second clock is the only thing that gives it away; without it the dead page would stay counted for as long as anybody else used the site and the count would only ever climb. Keep the open/close broadcast, and do not add a server-side keep-alive.
