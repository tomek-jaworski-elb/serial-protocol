const pathSession = '/session';
let socketSessions;

function createWebSocket() {
    const ws = new WebSocket(`${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.hostname}:${window.location.port}${pathSession}`);

    ws.onmessage = function (event) {
        console.log("WebSocket message received: ", event.data);
        const messagesContainer = document.getElementById('sessions');
        if (messagesContainer) {
            messagesContainer.innerHTML = event.data;
        }
    };

    ws.onerror = function (error) {
        console.error("WebSocket error: ", error);
    };

    ws.onopen = function () {
        console.log("WebSocket connection opened.");
    };

    ws.onclose = function () {
        console.log("WebSocket connection closed.");
    };

    return ws;
}

socketSessions = createWebSocket();

/*
 * A keep-alive from the page, not from the server.
 *
 * This connection would otherwise be silent between page opens and closes, and the container closes
 * one that has been idle for sixty seconds -- so on a quiet installation every page dropped out of
 * its own count after a minute and the number in the footer froze there.
 *
 * The keep-alive has to come from THIS side. Having the server send something on a timer keeps the
 * connections open too, but it also keeps open the ones belonging to pages that are already gone:
 * a window closed by a crash or a forced quit never sends a close frame, and the idle timeout is
 * the only thing that would ever have noticed. Writing to it resets that timeout, so the dead page
 * stays in the count for ever and the number only ever climbs. Sending from the page instead means
 * a page that stops existing stops sending, and the timeout does its job.
 *
 * Half the idle timeout, so one lost message is not enough to drop the connection.
 */
setInterval(function () {
    if (socketSessions && socketSessions.readyState === WebSocket.OPEN) {
        socketSessions.send('.');
    }
}, 30000);

/*
 * There is deliberately no visibilitychange handler here.
 *
 * The one that used to sit at this spot closed `socket` when the tab was hidden -- a variable this
 * file never declares. On pages carrying another script it closed THAT script's connection
 * (/json on the chart, /heartbeat on the home page), and on the rest it threw
 * "socket is not defined" on every tab switch.
 *
 * Repairing it to close socketSessions would be worse than leaving it broken: a hidden tab is
 * still an open page and has to keep being counted. This connection lives as long as the page.
 */
