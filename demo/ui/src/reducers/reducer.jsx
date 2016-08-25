import {Map, fromJS} from 'immutable';

function setState(state, newState) {
    return state.merge(newState);
}

function changeFilter(state, newFilter) {
    return state.update('partialNameFilter', f => newFilter );
}

function replaceInstances(state, newInstances) {
    return state.update('instances', i => fromJS(newInstances))
}

export default function(state = Map(), action) {
    switch (action.type) {
        case 'INITIAL_STATE':
            return setState(state, action.state);
        case 'REPLACE_INSTANCES':
            return replaceInstances(state, action.instances)

        case 'CHANGE_FILTER':
            return changeFilter(state, action.newFilter)
    }
    return state;
}