import fetch from 'isomorphic-fetch'

export const REQUEST_INSTANCES = 'REQUEST_INSTANCES'
export const CHANGE_FILTER = 'CHANGE_FILTER'
export const RECEIVE_INSTANCES = 'RECEIVE_INSTANCES'

export const changeFilter = (filter) => {
    return {
        type: 'CHANGE_FILTER',
        newFilter: filter
    }
}

export function receiveInstances(filter, json) {
    return {
        type: RECEIVE_INSTANCES,
        filter: filter,
        instances: json,
        receivedAt: Date.now()
    }
}

export function requestInstances(filter) {
    return {
        type: REQUEST_INSTANCES,
        filter: filter
    }
}

export function fetchInstances(filter) {

    // Thunk middleware knows how to handle functions.
    // It passes the dispatch method as an argument to the function,
    // thus making it able to dispatch actions itself.

    return function (dispatch) {

        // First dispatch: the app state is updated to inform
        // that the API call is starting.

        dispatch(requestInstances(filter))

        // The function called by the thunk middleware can return a value,
        // that is passed on as the return value of the dispatch method.

        // In this case, we return a promise to wait for.
        // This is not required by thunk middleware, but it is convenient for us.

        var headers = new Headers();

        headers.append('X-Okapi-Tenant', 'our');

        var init = { method: 'GET',
            headers: headers };

        return fetch(`http://localhost:9130/knowledge-base/instance?partialTitle=${filter}`, init)
            .then(response => response.json())
            .then(json =>

                // We can dispatch many times!
                // Here, we update the app state with the results of the API call.

                dispatch(receiveInstances(filter, json))
            )

        // In a real world app, you also want to
        // catch any error in the network call.
    }
}
