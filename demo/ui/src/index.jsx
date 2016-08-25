import 'babel-polyfill'
import React from 'react';
import ReactDOM from 'react-dom';
import {compose, createStore, applyMiddleware} from 'redux';
import {Provider} from 'react-redux';
import thunkMiddleware from 'redux-thunk'
import createLogger from 'redux-logger'

import reducer from './reducers/reducer';
import { fetchInstances } from './action_creators'

import {InstanceSearchContainer} from './components/InstanceSearch';

const loggerMiddleware = createLogger()

const store = createStore(
    reducer,
    applyMiddleware(
        thunkMiddleware, // lets us dispatch() functions
        loggerMiddleware // neat middleware that logs actions
    )
)

store.dispatch({
    type: 'INITIAL_STATE',
    state: {
        instances: [],
        partialNameFilter: ""
    }
});

store.dispatch(fetchInstances(''));

ReactDOM.render(
    <Provider store={store}>
        <InstanceSearchContainer />
    </Provider>,
    document.getElementById('app')
);
