import React from 'react';
import Instance from './Instance';
import {connect} from 'react-redux';

import * as actionCreators from '../action_creators';

export default class InstanceSearch extends React.Component {
    render() {
        return <div>
            <section className="instancesearch">
                <section className="main">
                    <label htmlFor="partialNameFilter">Partial Name: </label>
                    <input className="partialNameFilter"
                           defaultValue={this.props.partialNameFilter}
                           onChange={(event) => this.props.changeFilter(event.target.value) }
                            />
                    <button className="search" disabled="true">Search</button>
                    <ul className="results">
                        {this.props.instances
                            .filter(item => item.get('text').indexOf( this.props.partialNameFilter ) != -1 )
                            .map(item =>
                            <Instance key={item.get('text')}
                                      text={item.get('text')} />
                        )}
                        </ul>
                </section>
            </section>
        </div>
    }
};

function mapStateToProps(state) {
    return {
        instances: state.get('instances'),
        partialNameFilter: state.get('partialNameFilter')
    };
}

export const InstanceSearchContainer = connect(mapStateToProps, actionCreators)(InstanceSearch);