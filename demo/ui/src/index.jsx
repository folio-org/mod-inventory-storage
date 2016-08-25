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
        instances: [{id: 1, title: 'ADVANCING LIBRARY EDUCATION: TECHNOLOGICAL INNOVATION AND INSTRUCTIONAL DESIGN'}],
        partialNameFilter: ""
    }
});

store.dispatch({
    type: 'REPLACE_INSTANCES',
    instances: [
            {id: 1, title: 'ADVANCING LIBRARY EDUCATION: TECHNOLOGICAL INNOVATION AND INSTRUCTIONAL DESIGN'},
            {id: 2, title: 'ADVANCING RESEARCH METHODS WITH NEW TECHNOLOGIES.'}
        ]
});

ReactDOM.render(
    <Provider store={store}>
        <InstanceSearchContainer />
    </Provider>,
    document.getElementById('app')
);
