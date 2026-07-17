import useLedgerExtraction from '../hooks/useLedgerExtraction';

export default function LedgerAttachInput({onExtracted, disabled = false}) {
	const {file, setFile, status, error, extract} = useLedgerExtraction();
	const extracting = status === 'extracting';

	const handleExtract = async () => {
		const result = await extract();
		if (result) onExtracted(result);
	};

	return (
		<div className='ledger-attach-input panel'>
			<input
				type='file'
				accept='application/pdf,image/jpeg,image/png,image/webp'
				onChange={(e) => setFile(e.target.files?.[0] || null)}
				disabled={disabled || extracting}
				className='ledger-attach-file-input'
			/>
			<div className='ledger-attach-actions'>
				<button
					type='button'
					className='btn-pill'
					onClick={handleExtract}
					disabled={disabled || extracting || !file}
				>
					Extraer datos
				</button>
				{extracting && <span className='ledger-attach-status'>Extrayendo...</span>}
				{error && <p className='error-text'>{error}</p>}
			</div>
		</div>
	);
}
