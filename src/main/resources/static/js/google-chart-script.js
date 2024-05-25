google.charts.load("current", {
    "packages":["map"],
    // Note: you will need to get a mapsApiKey for your project.
    // See: https://developers.google.com/chart/interactive/docs/basic_load_libs#load-settings
    "mapsApiKey": "AIzaSyCzkFg2FnGR4Rv5ye_U0pstjyzRY5bEFXA"
});
google.charts.setOnLoadCallback(drawChart);
function drawChart() {
    var data = google.visualization.arrayToDataTable([
        ['Lat', 'Long', 'Name'],
        [19.0760,  72.8777, 'Mumbai'],
        [18.5204, 73.8567, 'Pune'],
        [19.1176, 72.9060, 'Powai'],
    ]);

    var map = new google.visualization.Map(document.getElementById('map_div'));
    map.draw(data, {
        showTooltip: true,
        showInfoWindow: true
    });
}