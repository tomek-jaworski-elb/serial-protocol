
    function getScaledPointsNew(oldX, oldY) {
        const map_scale_factor = 2.407;
        const staticShift_y = 506;                                                                                      // 506
        const staticShift_x = 64;                                                                                       //  64
        const scaleX = map_scale_factor; /// 2.4; // 3.61 / 1.5 ; /// 1;// 3.61;                   // 2.407                                                                               //   3.61
        const scaleY = map_scale_factor; /// 2.4; // 3.61 / 1.5 ; /// 1;// 3.61;
        //   3.61
        // Changed coordinate system x->y , y->x
        const y = (-oldX + staticShift_y) * scaleY;
        const x = (oldY + staticShift_x ) * scaleX;
        return {x, y};
    }