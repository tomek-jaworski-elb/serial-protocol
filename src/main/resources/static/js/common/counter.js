const ShipCounter = (function () {
    // Private map
    const incrementMap = new Map([
        [ModelsOfShips.WARTA.id, 0],
        [ModelsOfShips.BLEUE_LADY.id, 0],
        [ModelsOfShips.DORCHERTER_LADY.id, 0],
        [ModelsOfShips.CHERRY_LADY.id, 0],
        [ModelsOfShips.KOLOBRZEG.id, 0],
        [ModelsOfShips.LADY_MARIE.id, 0]
    ]);

    // Public function to increment the map's values
    function incrementIntMap(key) {
        if (incrementMap.has(key)) {
            incrementMap.set(key, incrementMap.get(key) + 1);
            if (incrementMap.get(key) > 999) {
                incrementMap.set(key, 0);
            }
            return incrementMap.get(key);
        } else {
            console.error("Key " + key + " does not exist in map");
        }
    }

    // Expose only the incrementIntMap function
    return {
        incrementIntMap
    };
})();