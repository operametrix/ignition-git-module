// Central access to @inductiveautomation/ignition-web-ui components.
//
// The package ships strict prop types intended largely for internal/platform use (e.g. DataGrid
// requires paginationParams/setTableQueryParams that have sensible runtime defaults). We re-export
// the components as untyped here and pass the props we need, mirroring the published storybook
// examples, rather than satisfying every required type.
import * as WebUI from "@inductiveautomation/ignition-web-ui";

const ui = WebUI as any;

export const Button = ui.Button;
export const Card = ui.Card;
export const Chip = ui.Chip;
export const DataGrid = ui.DataGrid;
export const Drawer = ui.Drawer;
export const DrawerTemplate = ui.DrawerTemplate;
export const DrawerTemplateSize = ui.DrawerTemplateSize;
export const DrawerTemplateColorTheme = ui.DrawerTemplateColorTheme;
export const Form = ui.Form;
export const FormControlInput = ui.FormControlInput;
export const Loading = ui.Loading;
export const Modal = ui.Modal;
export const PageHeader = ui.PageHeader;
export const Radio = ui.Radio;
export const SelectInput = ui.SelectInput;
export const TextArea = ui.TextArea;
export const TextAutocomplete = ui.TextAutocomplete;
export const TextInput = ui.TextInput;
export const Tooltip = ui.Tooltip;
