const stage = new Konva.Stage({
    container: 'main-div',
    width: 700,
    height: 400
});
const layer = new Konva.Layer();
stage.add(layer);

// Przykładowe obiekty
const rect = new Konva.Rect({
    x: 100,
    y: 100,
    width: 150,
    height: 80,
    fill: 'lightblue',
    stroke: 'blue',
    strokeWidth: 2
});

const circle = new Konva.Circle({
    x: 400,
    y: 200,
    radius: 50,
    fill: 'lightcoral',
    stroke: 'darkred',
    strokeWidth: 2
});

layer.add(rect, circle);

// popup (Label)
const popup = new Konva.Label({
    opacity: 1,
    visible: false,
    listening: false // nie blokuje zdarzeń
});

popup.add(new Konva.Tag({
    fill: 'white',
    stroke: 'black',
    strokeWidth: 3,
    cornerRadius: 6,
    shadowColor: 'black',
    shadowBlur: 5,
    shadowOffset: { x: 2, y: 2 },
    shadowOpacity: 0.4
}));

const popupText = new Konva.Text({
    text: '',
    fontFamily: 'Arial',
    fontSize: 14,
    padding: 6,
    fill: 'black'
});

const line = new Konva.Line({
    points: [0, 0, 300, 300, 20, 100, 150, 400], // x1, y1, x2, y2
    stroke: 'black',
    strokeWidth: 3,
    lineCap: 'round',
    lineJoin: 'round'
})

popup.add(popupText);
layer.add(popup);
layer.add(line);
layer.draw();

// event: kliknięcie w obiekt
stage.on('click', (e) => {
    if (e.target === stage) {
        popup.hide();
        layer.batchDraw();
        return;
    }

    const shape = e.target;
    const mousePos = stage.getPointerPosition();

    const pos = shape.getAbsolutePosition();
    popupText.text(`x: ${pos.x.toFixed(1)}\ny: ${pos.y.toFixed(1)}`);

    // ustaw pozycję popupu nad myszką
    popup.position({
        x: mousePos.x + 10,
        y: mousePos.y - 40
    });

    popup.show();
    layer.batchDraw();
});

// kliknięcie w tło — ukryj popup
stage.on('mousedown', (e) => {
    if (e.target === stage) {
        popup.hide();
        layer.batchDraw();
    }
});