module.exports = {
    entry: [
        './src/index.js'
    ],
    // module: {
    //     loaders: [{
    //         test: /\.jsx?$/,
    //         exclude: /node_modules/,
    //     }]
    // },
    resolve: {
        extensions: ['', '.js']
    },
    output: {
        path: __dirname + '/dist',
        publicPath: '/',
        filename: 'bundle.js'
    },
    devServer: {
        contentBase: './dist'
    }
};