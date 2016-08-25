import React from 'react';
import ReactDOM from 'react-dom';
import { createStore } from 'redux'
import {Provider} from 'react-redux';

import reducer from './reducers/reducer';

import {InstanceSearchContainer} from './components/InstanceSearch';

const store = createStore(reducer);

store.dispatch({
    type: 'INITIAL_STATE',
    state: {
        instances: [
            {id: 1, text: 'ADVANCING LIBRARY EDUCATION: TECHNOLOGICAL INNOVATION AND INSTRUCTIONAL DESIGN'},
            {id: 2, text: 'ADVANCING RESEARCH METHODS WITH NEW TECHNOLOGIES.'},
        ],
        partialNameFilter: "LIBRARY"
    }
});

store.dispatch({
    type: 'CHANGE_FILTER',
    newFilter: "RESEARCH"
    });

ReactDOM.render(
    <Provider store={store}>
        <InstanceSearchContainer />
    </Provider>,
    document.getElementById('app')
);