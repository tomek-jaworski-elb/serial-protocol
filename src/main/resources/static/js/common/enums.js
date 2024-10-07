
const ModelsOfShips = Object.freeze({
    WARTA: {id: 1, color: "orange", name: "Warta"},
    BLEUE_LADY: {id: 2, color: "blue", name: "Blue Lady"},
    DORCHERTER_LADY: {id: 3, color: "green", name: "Dorchester Lady"},
    CHERRY_LADY: {id: 4, color: "purple", name: "Cherry Lady"},
    KOLOBRZEG: {id: 5, color: "lightgray", name: "Kołobrzeg"},
    LADY_MARIE: {id: 6, color: "darkblue", name: "Lady Marie"},

    getValueFromId(id) {
        return Object.values(ModelsOfShips).find(ship => ship.id === id);
    },

    getColorFromId(id) {
        const ship = ModelsOfShips.getValueFromId(id);
        return ship ? ship.color.toString() : null; // Return the color or null if not found
    }
});