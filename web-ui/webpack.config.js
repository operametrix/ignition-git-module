const path = require("path");
const webpack = require("webpack");
const ForkTsCheckerWebpackPlugin = require("fork-ts-checker-webpack-plugin");
const ESLintPlugin = require("eslint-webpack-plugin");
const HtmlWebpackPlugin = require("html-webpack-plugin");

module.exports = (webpackConfigEnv, argv) => {
  const { WEBPACK_SERVE = false, isStandalone = false } = webpackConfigEnv;
  const { mode = "development" } = argv;

  let devPlugins = [];
  let rootAppFileName = "index.ts";
  let openPage = [];

  // Provided by the gateway application — do not bundle these.
  let externals = [
    "react",
    "react-dom",
    "react-router-dom",
    "react-redux",
    "@reduxjs/toolkit",
    "@inductiveautomation/ignition-web-ui",
    "@inductiveautomation/ignition-icons",
    "luxon",
  ];

  const envKeys = Object.keys(webpackConfigEnv).reduce((prev, next) => {
    prev[`process.env.${next}`] = JSON.stringify(webpackConfigEnv[next]);
    return prev;
  }, {});

  if (WEBPACK_SERVE && isStandalone) {
    devPlugins.push(
      new HtmlWebpackPlugin({
        inject: false,
        template: "src/index.html",
        filename: "index.html",
        minify: false,
      })
    );
    openPage = ["http://localhost:9999/"];
    rootAppFileName = "root.component.js";
    externals = [];
  }

  return {
    mode,
    entry: {
      gitConfig: [path.join(__dirname, `src/${rootAppFileName}`)],
    },
    output: {
      library: "[name]",
      libraryTarget: "umd",
      umdNamedDefine: true,
      filename: "[name].js",
      publicPath: "",
      path: path.resolve(__dirname, "build/generated-resources/mounted/"),
    },
    context: path.resolve(__dirname),
    module: {
      rules: [
        {
          test: /\.css$|.scss$/,
          use: [
            { loader: "style-loader" },
            { loader: "css-loader" },
            { loader: "sass-loader" },
          ],
        },
        {
          test: /\.[tj]sx?$|\.d\.ts$/,
          use: [{ loader: "ts-loader" }, { loader: "babel-loader" }],
          exclude: /node_modules/,
        },
        {
          test: /\.(woff|woff2|eot|ttf|otf)$/i,
          type: "asset/inline",
        },
      ],
    },
    devtool: "source-map",
    devServer: {
      port: 9999,
      historyApiFallback: true,
      headers: { "Access-Control-Allow-Origin": "*" },
      client: { webSocketURL: { hostname: "localhost" } },
      allowedHosts: "all",
      open: openPage,
    },
    plugins: [
      new webpack.DefinePlugin(envKeys),
      ...devPlugins,
      new ForkTsCheckerWebpackPlugin(),
      new ESLintPlugin({ files: "./src/**/*.{ts,tsx,js,jsx}" }),
    ],
    resolve: {
      modules: ["node_modules"],
      extensions: [".js", ".jsx", ".scss", ".css", ".ts", ".tsx", ".d.ts"],
    },
    externals,
  };
};
