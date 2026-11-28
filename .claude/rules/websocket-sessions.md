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

Loaded when you touch the WebSocket configuration, handlers, publisher, `SessionType`, the `ws-*.js` scripts or the shared footer. Invariants: count live map viewers with `liveMapViewerCount()`, liveness comes only from page traffic, `SessionType` is a Java-to-JS contract (`CLAUDE.md`).

## WebSocket channels
| Channel | Content |
|---|---|
| `/rs` | raw serial frame as byte-array string |
| `/json` | `ModelTrackDTO` as JSON |
| `/heartbeat` | heartbeat tick (`ws.heartbeat.interval`) |
| `/session` | live-map-viewer count (one connection per loaded page, from the footer; `/chart` also sends `watching`/`hidden`) |

`SessionType` enum is the shared contract between Java and JS. `WebSocketConfiguration` wires one handler per channel (`RSWebsocketHandler`, `JsonWebSocketHandler`, `HeartBeatWebSocketHandler`, `SessionCountWebSockerHandler` — the typo in that class name is real). `WSSessionManager` stores connections globally; `WebSocketPublisherImpl` fans out via `ThreadPoolExecutorConfig` thread pool.

Ask for the number of live map viewers with `WebSocketPublisher.liveMapViewerCount()`, never by counting the whole registry: a page holds one to three connections depending on what it displays. The count is the open `/session` connections whose `WATCHING_LIVE_MAP` attribute is true. Only `/chart` sends `watching` (on open and when the tab becomes visible) or `hidden` (tab hidden); `SessionCountWebSockerHandler` stores it and broadcasts only when the value changes. `.` changes nothing. The two payloads are a Java-to-JS contract like `SessionType`. A page shown again reconnects `/session` if it was closed meanwhile (mobile suspension, back/forward cache), and its `onopen` reports the state anew.

`SessionCountWebSockerHandler` broadcasts the count to every page when a connection opens or closes. A `@Scheduled` sweep, `closeSilentPages()`, runs every `ws.session.silence-check` (30000 ms) and closes `/session` connections that have sent nothing for longer than `ws.session.silence-limit` (150s — generous because a background tab's timers are throttled to roughly once a minute). The sweep does not broadcast itself; closing a connection triggers `afterConnectionClosed`, which removes it and then broadcasts.

## "Session" means four different things here
Be precise about which one you mean, because conflating them is what broke the footer:

| Term | What it is |
|---|---|
| **channel** | a named stream (`/rs`, `/json`, `/heartbeat`, `/session`) — `SessionType` calls these "session types", which is a misnomer |
| **connection** | one page subscribed to one channel; `WSSessionManager` holds all of them |
| **login session** | a signed-in user's server-side session, `server.servlet.session.timeout` |
| **open page** | one loaded page, from load until its tab closes |

The footer's **"Live map viewers"** counts **visible `/chart` tabs** (one per tab, no per-user dedup); `/terminal`, `/tracks`, other pages and hidden chart tabs count 0. It works because the footer is on every page and opens exactly one `/session` connection, which `/chart` marks `watching`/`hidden`. Counting connections instead counted subscriptions, and a page holds one to three of them. A hidden tab keeps its connection and keep-alive; it only stops counting.

A page also sends a keep-alive on that connection every 30s (`js/ws-session-count.js`). It has to come from the page, and the reason is that there are two different clocks:

- The **container idle timeout** (`WebSocketConfiguration`, `setMaxSessionIdleTimeout`, 3 minutes) is reset by traffic in *either* direction. The server writes to every open page whenever another page opens or closes (`broadcastLiveMapViewerCount()`, which is required), and that write resets this clock for pages that are already gone too.
- **`lastHeardFrom`** is written only when the *page* sends something. `closeSilentPages()` compares it with `ws.session.silence-limit`. Server writes cannot touch it.

A window killed by a crash never sends a close frame, so silence on the second clock is the only thing that gives it away; without it the dead page would stay counted for as long as anybody else used the site and the count would only ever climb. Keep the open/close broadcast, and do not add a server-side keep-alive.

A fifth, unrelated meaning is the HTTP-session `SessionRegistry` of Spring Security (`SecurityConfig`), which tracks logins so admin changes can expire them; it has nothing to do with `WSSessionManager` or `liveMapViewerCount()`.
