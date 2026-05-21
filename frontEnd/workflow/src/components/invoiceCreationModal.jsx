import InvoiceCreateForm from '../views/invoiceCreateForm.jsx';

export default function InvoiceCreationModal({isOpen, onClose}) {
	if (!isOpen) return null;

	return (
		<div className='modal-overlay'>
			<div className='modal-content'>
				<InvoiceCreateForm isModal onClose={onClose} />
			</div>
		</div>
	);
}
