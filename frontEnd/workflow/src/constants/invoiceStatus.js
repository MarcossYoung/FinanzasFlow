// Allowed "Estado" values for the Facturas (invoice) UI.
// NOTE: the backend `Status` enum still holds the full cobranzas set
// (CONTACTADO, PROMETIO_PAGO, EN_DISPUTA, INCOBRABLE, ...). This list only
// governs what the invoice screens let a user pick — it is a UI-only restriction.
export const INVOICE_STATUS_OPTIONS = [
	{value: 'EN_GESTION', label: 'En gestión'},
	{value: 'CANCELADO', label: 'Cancelado'},
	{value: 'CERRADO', label: 'Cobrada'},
];

// Label lookup that still includes the legacy cobranzas values, so an existing
// invoice that carries a now-removed status renders correctly (it just can't be
// re-selected from the dropdowns).
export const STATUS_LABELS = {
	EN_GESTION: 'En gestión',
	CANCELADO: 'Cancelado',
	CERRADO: 'Cobrada',
	CONTACTADO: 'Contactado',
	PROMETIO_PAGO: 'Prometió pago',
	EN_DISPUTA: 'En disputa',
	INCOBRABLE: 'Incobrable',
};

export const statusLabel = (status) =>
	STATUS_LABELS[status] ?? status ?? 'En gestión';
