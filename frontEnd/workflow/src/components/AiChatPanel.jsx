import React, {useState, useRef, useEffect, useContext} from 'react';
import {UserContext} from '../UserProvider';
import {BASE_URL} from '../api/config';
import {useLocation} from 'react-router-dom';

export default function AiChatPanel() {
	const {user} = useContext(UserContext);
	const location = useLocation();
	const [open, setOpen] = useState(false);
	const [messages, setMessages] = useState([]);
	const [input, setInput] = useState('');
	const [streaming, setStreaming] = useState(false);
	const messagesEndRef = useRef(null);
	const abortRef = useRef(null);

	useEffect(() => {
		messagesEndRef.current?.scrollIntoView({behavior: 'smooth'});
	}, [messages]);

	if (location.pathname === '/login') return null;

	// Only show for client-facing operators and admins.
	if (!user || (user.role !== 'GESTOR' && user.role !== 'ADMIN' && user.role !== 'SUPER_ADMIN')) return null;

	// Derive current page from URL
	const getPage = () => {
		const path = location.pathname;
		if (path.includes('finance')) return 'finance';
		if (path.includes('dashboard')) return 'dashboard';
		if (path.includes('costs')) return 'costs';
		return 'other';
	};

	const sendMessage = async () => {
		const text = input.trim();
		if (!text || streaming) return;
		setInput('');

		const newMessages = [...messages, {role: 'user', content: text}];
		setMessages(newMessages);
		setStreaming(true);

		// Add placeholder for assistant response
		setMessages(prev => [...prev, {role: 'assistant', content: ''}]);

		try {
			const token = user?.token || localStorage.getItem('token');
			const history = newMessages.slice(-5, -1).map(m => ({role: m.role, content: m.content}));

			const controller = new AbortController();
			abortRef.current = controller;

			const response = await fetch(`${BASE_URL}/api/ai/chat`, {
				method: 'POST',
				headers: {
					'Content-Type': 'application/json',
					'Authorization': `Bearer ${token}`,
				},
				body: JSON.stringify({message: text, page: getPage(), history}),
				signal: controller.signal,
			});

			if (!response.ok) throw new Error('Error en la respuesta');

			const reader = response.body.getReader();
			const decoder = new TextDecoder();
			let assistantText = '';

			while (true) {
				const {done, value} = await reader.read();
				if (done) break;
				const chunk = decoder.decode(value, {stream: true});
				// Parse SSE lines: "data: text\n"
				const lines = chunk.split('\n');
				for (const line of lines) {
					if (line.startsWith('data:')) {
						const delta = line.slice(5).trim();
						if (delta) {
							assistantText += delta;
							const snapshot = assistantText;
							setMessages(prev => {
								const updated = [...prev];
								updated[updated.length - 1] = {role: 'assistant', content: snapshot};
								return updated;
							});
						}
					}
				}
			}
		} catch (err) {
			if (err.name !== 'AbortError') {
				setMessages(prev => {
					const updated = [...prev];
					updated[updated.length - 1] = {role: 'assistant', content: 'Error al conectar con la IA.'};
					return updated;
				});
			}
		} finally {
			setStreaming(false);
		}
	};

	const handleKeyDown = (e) => {
		if (e.key === 'Enter' && !e.shiftKey) {
			e.preventDefault();
			sendMessage();
		}
	};

	return (
		<>
			{/* Toggle Button */}
			<button
				onClick={() => setOpen(o => !o)}
				style={{
					position: 'fixed',
					bottom: 24,
					right: 24,
					width: 52,
					height: 52,
					borderRadius: '50%',
					background: '#6c5ce7',
					color: 'white',
					border: 'none',
					cursor: 'pointer',
					fontSize: '1.3rem',
					boxShadow: '0 4px 16px rgba(108,92,231,0.5)',
					zIndex: 9999,
					display: 'flex',
					alignItems: 'center',
					justifyContent: 'center',
				}}
				title='Asistente IA'
			>
				{open ? '✕' : '💬'}
			</button>

			{/* Chat Panel */}
			{open && (
				<div style={{
					position: 'fixed',
					bottom: 88,
					right: 24,
					width: 340,
					height: 460,
					background: 'white',
					borderRadius: '16px',
					boxShadow: '0 8px 32px rgba(0,0,0,0.15)',
					display: 'flex',
					flexDirection: 'column',
					zIndex: 9998,
					overflow: 'hidden',
					border: '1px solid #eee',
				}}>
					{/* Header */}
					<div style={{background: 'linear-gradient(135deg, #6c5ce7, #a29bfe)', padding: '14px 16px', color: 'white'}}>
						<strong style={{fontSize: '0.95rem'}}>Asistente IA</strong>
						<p style={{margin: '2px 0 0', fontSize: '0.75rem', opacity: 0.85}}>
							Preguntame sobre facturas y finanzas
						</p>
					</div>

					{/* Messages */}
					<div style={{flex: 1, overflowY: 'auto', padding: '12px', display: 'flex', flexDirection: 'column', gap: '10px'}}>
						{messages.length === 0 && (
							<p style={{color: '#b2bec3', fontSize: '0.85rem', textAlign: 'center', marginTop: '20px'}}>
								Hola! ¿En qué puedo ayudarte?
							</p>
						)}
						{messages.map((m, i) => (
							<div
								key={i}
								style={{
									alignSelf: m.role === 'user' ? 'flex-end' : 'flex-start',
									maxWidth: '85%',
									background: m.role === 'user' ? '#6c5ce7' : '#f5f6fa',
									color: m.role === 'user' ? 'white' : '#2d3436',
									padding: '8px 12px',
									borderRadius: m.role === 'user' ? '12px 12px 2px 12px' : '12px 12px 12px 2px',
									fontSize: '0.875rem',
									lineHeight: '1.5',
									whiteSpace: 'pre-wrap',
								}}
							>
								{m.content || (m.role === 'assistant' && streaming ? '...' : '')}
							</div>
						))}
						<div ref={messagesEndRef} />
					</div>

					{/* Input */}
					<div style={{padding: '10px 12px', borderTop: '1px solid #f0f0f0', display: 'flex', gap: '8px'}}>
						<input
							type='text'
							value={input}
							onChange={e => setInput(e.target.value)}
							onKeyDown={handleKeyDown}
							placeholder='Escribe tu pregunta...'
							disabled={streaming}
							style={{
								flex: 1,
								padding: '8px 12px',
								borderRadius: '20px',
								border: '1px solid #dfe6e9',
								fontSize: '0.875rem',
								outline: 'none',
							}}
						/>
						<button
							onClick={sendMessage}
							disabled={streaming || !input.trim()}
							style={{
								padding: '8px 14px',
								background: '#6c5ce7',
								color: 'white',
								border: 'none',
								borderRadius: '20px',
								cursor: streaming ? 'not-allowed' : 'pointer',
								opacity: streaming ? 0.6 : 1,
								fontSize: '0.875rem',
							}}
						>
							{streaming ? '...' : '↑'}
						</button>
					</div>
				</div>
			)}
		</>
	);
}
