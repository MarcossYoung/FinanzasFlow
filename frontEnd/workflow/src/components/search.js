import React, { useState } from "react";

const InvoiceSearch = ({ onSearch }) => {
  const [query, setQuery] = useState("");

  const handleSearch = () => {
    onSearch(query);
  };

  return (
    <div>
      <input
        type="text"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Buscar facturas..."
      />
      <button onClick={handleSearch}>Buscar</button>
    </div>
  );
};

export default InvoiceSearch;
