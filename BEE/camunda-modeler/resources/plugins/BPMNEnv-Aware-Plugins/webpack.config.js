const path = require('path');

module.exports = {
  entry: './client.js',
  output: {
    path: path.resolve(__dirname, 'dist'),
    filename: 'client.js',
    libraryTarget: 'umd'
  },
  mode: 'production',
  devtool: 'source-map',
   externals: {
    preact: 'preact',
    'preact/hooks': 'preact/hooks'
  },
  module: {
    rules: [
      {
        test: /\.js$/,
        exclude: /node_modules/,
        use: {
          loader: 'babel-loader',
          options: {
            presets: [ [ '@babel/preset-env', { targets: { chrome: '114' } } ] ]
          }
        }
      },
      {
        test: /\.svg$/i,
        type: 'asset/inline'  // <-- makes import icon from '../icons/x.svg' return a data URI
      }
    ]
  }
};
