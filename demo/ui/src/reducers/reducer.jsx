import {Map} from 'immutable';

function setState(state, newState) {
    return state.merge(newState);
}

function changeFilter(state, newFilter) {
    return state.update('partialNameFilter', partialName => partialName = newFilter );
}

export default function(state = Map(), action) {
    switch (action.type) {
        case 'INITIAL_STATE':
            return setState(state, action.state);
        case 'CHANGE_FILTER':
            return changeFilter(state, action.newFilter)
    }
    return state;
}