-- Demo users and instruments. Prices are simulated; PUT /api/instruments/{symbol}/price moves them.

INSERT INTO users (name, email, cash_balance) VALUES
    ('Alice Johnson', 'alice@example.com', 100000.00),
    ('Bob Smith',     'bob@example.com',    50000.00),
    ('Charlie Davis', 'charlie@example.com', 10000.00);

INSERT INTO instruments (symbol, name, current_price) VALUES
    ('AAPL',  'Apple Inc.',                 227.50),
    ('MSFT',  'Microsoft Corporation',      415.30),
    ('GOOGL', 'Alphabet Inc. Class A',      172.80),
    ('AMZN',  'Amazon.com Inc.',            186.40),
    ('NVDA',  'NVIDIA Corporation',         121.75),
    ('TSLA',  'Tesla Inc.',                 248.90),
    ('META',  'Meta Platforms Inc.',        563.20),
    ('JPM',   'JPMorgan Chase & Co.',       212.60),
    ('V',     'Visa Inc.',                  278.15),
    ('KO',    'The Coca-Cola Company',       69.85);
