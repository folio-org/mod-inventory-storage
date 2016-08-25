import React from 'react';
import Instance from './Instance';
import {connect} from 'react-redux';

import { changeFilter, fetchInstances } from '../action_creators';

export default class InstanceSearch extends React.Component {
    render() {
        let newFilter

        return <div>
            <section className="instancesearch">
                <section className="main">
                    <form onSubmit={e => {
                        e.preventDefault()
                        this.props.onSearchSubmit(newFilter.value)
                    }}>
                        <label htmlFor="partialNameFilter">Partial Name: </label>
                        <input className="partialNameFilter" ref = {node => { newFilter = node }}
                               defaultValue={this.props.partialNameFilter} />
                        <button className="search">Search</button>
                    </form>
                    <ul className="results">
                        {this.props.instances
                            .map(item =>
                            <Instance key={item.get('title')}
                                      title={item.get('title')} />
                        )}
                        </ul>
                </section>
            </section>
        </div>
    }
};

const mapStateToProps = (state) => {
    return {
        instances: state.get('instances'),
        partialNameFilter: state.get('partialNameFilter')
    };
}

const mapDispatchToProps = (dispatch) => {
    return {
        onSearchSubmit: (newFilter) => {
            dispatch(changeFilter(newFilter));
            dispatch(fetchInstances(newFilter));
        }
    }
}

export const InstanceSearchContainer = connect(mapStateToProps, mapDispatchToProps)(InstanceSearch);