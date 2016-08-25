import React from 'react';

export default class Instance extends React.Component {
    render() {
        return <li className="instance">
                    <div className="view">
                        <label> {this.props.title} </label>
                        <button className="createItem" disabled="true">Create Item</button>
                    </div>
                </li>
    }
};
