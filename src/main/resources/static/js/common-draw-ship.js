
    function drawShip(elementId, x, y, scale, angle, fillColor, yy, xx, pp) {
        const element = document.getElementById(elementId);
        const ctx = element.getContext('2d');
        // Define the outline of the ship (ship with the bow at the beginning)
        let vertices = [
            {x:       0, y: -0.5*yy + pp*(yy/10)},                                                                      // nr1    //      1      |
            {x:  0.5*xx, y: -0.4*yy + pp*(yy/10)},                                                                      // nr2    //   2     5
            {x:  0.5*xx, y:  0.5*yy + pp*(yy/10)},                                                                      // nr3    //      o      y
            {x: -0.5*xx, y:  0.5*yy + pp*(yy/10)},                                                                      // nr4    //
            {x: -0.5*xx, y: -0.4*yy + pp*(yy/10)}                                                                       // nr5    //   3     4   |
        ];                                                                                                              //             -  x  -

        // Scale the vertices
        vertices = vertices.map(vertex => {
            return {
                x: vertex.x * scale * 1.1 ,                                                                             //  * 1.1
                y: vertex.y * scale
            };
        });

        // Rotate the vertices
        const radians = angle * Math.PI / 180;
        vertices = vertices.map(vertex => {
            return {
                x: vertex.x * Math.cos(radians) - vertex.y * Math.sin(radians),
                y: vertex.x * Math.sin(radians) + vertex.y * Math.cos(radians)
            };
        });

        // Translate the vertices to the (x, y) position
        vertices = vertices.map(vertex => {
            return {
                x: vertex.x + x,
                y: vertex.y + y
            };
        });

        // Draw the ship
        ctx.beginPath();
        ctx.moveTo(vertices[0].x, vertices[0].y);
        for (let i = 1; i < vertices.length; i++) {
            ctx.lineTo(vertices[i].x, vertices[i].y);
        }
        ctx.closePath();

        // Fill and stroke
        ctx.fillStyle = fillColor;
        ctx.fill();
        ctx.strokeStyle = 'black';
        ctx.lineWidth = 1;
        ctx.stroke();
    }

    const mapa_x = 2.407;

    function getScaledPoints(oldX, oldY) {
        const staticShift_y = 506;                                                                                      // 506
        const staticShift_x = 64;                                                                                       //  64
        scaleX = mapa_x; /// 2.4; // 3.61 / 1.5 ; /// 1;// 3.61;                   // 2.407                                                                               //   3.61
        scaleY = mapa_x; /// 2.4; // 3.61 / 1.5 ; /// 1;// 3.61;                                                                                                  //   3.61
        console.log("ScaleX: " + scaleX + ", ScaleY: " + scaleY);
        console.log("Old X: " + oldX + ", Old Y:  " + oldY)
        // Changed coordinate system x->y , y->x
        const y = (-oldX + staticShift_y) * scaleY;
        const x = (oldY + staticShift_x ) * scaleX;
        console.log("New X: " + x + ", New Y: " +  y);
        return {x, y};
    }